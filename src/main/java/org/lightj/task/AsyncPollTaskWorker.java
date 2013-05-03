package org.lightj.task;

import java.util.concurrent.TimeUnit;

import org.lightj.task.WorkerMessage.CallbackType;

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.japi.Function;


/**
 * Provides abstraction of an operation with polling.
 * 
 *  @author biyu
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AsyncPollTaskWorker<T extends ExecutableTask> extends UntypedActor {
	
	/** task, poll option */
	private T task;
	private final AsyncPollMonitor monitor;
	
	/** intermediate result */
	private TaskResult taskSubmissionResult;
	private TaskResult taskPollResult;
	private TaskResult curResult;
	private boolean requestDone;
	
	/** runtime */
	private final SupervisorStrategy supervisorStrategy;
	private ActorRef asyncWorker = null;
	private ActorRef pollWorker = null;

	/** requester */
	private ActorRef sender = null;
	private volatile int tryCount = 0;
	private volatile int pollTryCount = 0;
	
	/** any unfinished business */
	private Cancellable retryMessageCancellable = null;
	private Cancellable pollMessageCancellable = null;

	/** any unfinished operation and their schedule */
	private Cancellable timeoutMessageCancellable = null;
	private FiniteDuration timeoutDuration = null;

	/** internal message type */
	private enum InternalMessageType {
		RETRY_REQUEST, PROCESS_ON_TIMEOUT, POLL_PROGRESS, PROCESS_REQUEST_RESULT, PROCESS_POLL_RESULT
	}

	/**
	 * constructor with single task
	 * @param task
	 * @param monitorOptions
	 * @param listener
	 */
	public AsyncPollTaskWorker(AsyncPollMonitor monitor) {
		super();
		this.monitor = monitor;
		
		// Other initialization
		this.supervisorStrategy = new OneForOneStrategy(0, Duration.Inf(), new Function<Throwable, Directive>() {
			public Directive apply(Throwable arg0) {
				getSelf().tell(task.createErrorResult(TaskResultEnum.Failed, "AsyncPollWorker crashed", arg0), getSelf());
				return SupervisorStrategy.stop();
			}
		});
	}

	@Override
	public void onReceive(Object message) throws Exception 
	{
		try {
			// This is original request from external sender
			if (message instanceof ExecutableTask) {
				
				task = (T) message;
				sender = getSender();
				processRequest();
				if (tryCount == 0) {
					replyTask(CallbackType.created, task);
				}
			
			}
			// Internal messages
			else if (message instanceof InternalMessageType) {
				
				switch ((InternalMessageType) message) {
				
				case RETRY_REQUEST:
					processRequest();
					break;

				case POLL_PROGRESS:
					pollProgress();
					break;

				case PROCESS_REQUEST_RESULT:
					processRequestResult();
					break;
					
				case PROCESS_POLL_RESULT:
					processPollResult();
					break;
				}
				
			} 
			else if (message instanceof TaskResult) {
				final TaskResult r = (TaskResult) message;
				handleWorkerResponse(r);
			} 
			else {
				unhandled(message);
			}
		} 
		catch (Throwable e) {
			retry(TaskResultEnum.Failed, e.toString(), e);
		}
	}
	
	/**
	 * send request
	 */
	private final void processRequest() 
	{
		if (asyncWorker == null) {
			asyncWorker = getContext().actorOf(new Props(new UntypedActorFactory() {
				private static final long serialVersionUID = 1L;

				public Actor create() {
					return createRequestWorker(task);
				}
				
			}));
		}
		
		asyncWorker.tell(task, getSelf());

		// asynchronous, set timeout
		if (tryCount == 0 && task.getExecOptions().hasTimeout()) {
			timeoutDuration = Duration.create(task.getExecOptions().getTimeoutInMs(), TimeUnit.MILLISECONDS);
			timeoutMessageCancellable = getContext()
					.system()
					.scheduler()
					.scheduleOnce(timeoutDuration, getSelf(),
							InternalMessageType.PROCESS_ON_TIMEOUT,
							getContext().system().dispatcher());
		}

	}

	/**
	 * process request result
	 */
	private final void processRequestResult() {
		
		if (curResult.getStatus().isAnyError()) {
			retry(curResult.getStatus(), curResult.getMsg(), curResult.getStackTrace());
		}
		else if (curResult.getStatus().isComplete()) {
			taskSubmissionResult = curResult;
			requestDone = true;
			replyTask(CallbackType.submitted, task);
			getSelf().tell(InternalMessageType.POLL_PROGRESS, getSelf());
		}
		
	}
	
	/**
	 * poll progress
	 */
	private final void pollProgress() {
		
		if (pollWorker == null) {
			pollWorker = getContext().actorOf(new Props(new UntypedActorFactory() {
				private static final long serialVersionUID = 1L;

				public Actor create() {
					return createPollWorker(task, taskSubmissionResult);
				}
				
			}));
		}

		ExecutableTask pollTask = monitor.createPollTask(task, curResult);
		pollWorker.tell(pollTask, getSelf());
		
	}
	
	/**
	 * process polling result
	 */
	private final void processPollResult() {

		boolean scheduleNextPoll = true;
		if (curResult.getStatus().isAnyError()) {
			retry(curResult.getStatus(), curResult.getMsg(), curResult.getStackTrace());
		}
		else if (curResult.getStatus().isComplete()) {
			TaskResult realResult = monitor.processPollResult(curResult);
			taskPollResult = realResult;
			scheduleNextPoll = (realResult == null || !realResult.isComplete()); 
		}

		if (scheduleNextPoll) {
			// Schedule next poll
			pollMessageCancellable = getContext()
					.system()
					.scheduler()
					.scheduleOnce(Duration.create(monitor.getMonitorOption().getMonitorIntervalMs(), TimeUnit.MILLISECONDS), getSelf(),
							InternalMessageType.POLL_PROGRESS, getContext().system().dispatcher());
		}
		else {
			reply(taskPollResult);
		}
		
	}

	/**
	 * worker response
	 * @param r
	 * @throws Exception
	 */
	private final void handleWorkerResponse(TaskResult r) throws Exception {
		
		curResult = r;
		getSelf().tell(requestDone ? InternalMessageType.PROCESS_POLL_RESULT : InternalMessageType.PROCESS_REQUEST_RESULT, getSelf());
		
	}

	/**
	 * handle retry
	 * @param status
	 * @param errorMessage
	 * @param stackTrace
	 */
	private final void retry(final TaskResultEnum status, final String errorMessage, final Throwable stackTrace) {
		// Error response
		boolean retried = false;
		if (requestDone) {
			if (pollTryCount++ < monitor.getMonitorOption().getMaxRetry()) {
				// noop, scheduled poll is same as retry
				retried = true;
			} 
		}
		else {
			if (tryCount++ < task.getExecOptions().getMaxRetry()) {
				retryMessageCancellable = getContext()
						.system()
						.scheduler()
						.scheduleOnce(Duration.create(task.getExecOptions().getRetryDelayMs(), TimeUnit.MILLISECONDS), getSelf(),
								InternalMessageType.RETRY_REQUEST, getContext().system().dispatcher());
				retried = true;
			} 
		}
		if (!retried) {
			// We have exceeded all retries, reply back to sender
			// with the error message
			replyError(status, (requestDone ? "request" : "poll") + " retry limit reached, last error: " + errorMessage, stackTrace);
		}
	}

	@Override
	public void postStop() {
		if (retryMessageCancellable != null && !retryMessageCancellable.isCancelled()) {
			retryMessageCancellable.cancel();
		}
		if (timeoutMessageCancellable != null && !timeoutMessageCancellable.isCancelled()) {
			timeoutMessageCancellable.cancel();
		}
		if (pollMessageCancellable != null && !pollMessageCancellable.isCancelled()) {
			pollMessageCancellable.cancel();
		}
		if (asyncWorker != null && !asyncWorker.isTerminated()) {
			asyncWorker.tell(PoisonPill.getInstance(), null);
		}
		if (pollWorker != null && !pollWorker.isTerminated()) {
			pollWorker.tell(PoisonPill.getInstance(), null);
		}
	}
	
	/**
	 * send non result callbacks
	 * @param type
	 * @param task
	 */
	private final void replyTask(CallbackType type, Task task) {
		if (!getContext().system().deadLetters().equals(sender)) {
			sender.tell(new WorkerMessage(type, task, null), getSelf());
		}
	}

	/**
	 * send error result
	 * @param state
	 * @param msg
	 * @param stackTrace
	 */
	private final void replyError(TaskResultEnum state, String msg, Throwable stackTrace) {
		TaskResult tr = task.createErrorResult(state, msg, stackTrace);
		if (!getContext().system().deadLetters().equals(sender)) {
			sender.tell(new WorkerMessage(CallbackType.taskresult, task, tr), getSelf());
		}
	}
	
	/**
	 * send result
	 * @param taskResult
	 */
	private final void reply(final TaskResult taskResult) {
		if (!getContext().system().deadLetters().equals(sender)) {
			sender.tell(new WorkerMessage(CallbackType.taskresult, task, taskResult), getSelf());
		}
	}

	@Override
	public SupervisorStrategy supervisorStrategy() {
		return supervisorStrategy;
	}
	
	/**
	 * create poll process
	 * @param task
	 * @return
	 */
	public Actor createRequestWorker(T task) {
		return new ExecutableTaskWorker();
	}
	
	/**
	 * create poll request based on result of the original request
	 * @param task
	 * @param result
	 * @return
	 */
	public Actor createPollWorker(T task, TaskResult result) {
		return new ExecutableTaskWorker();
	}
	
}