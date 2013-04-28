package org.lightj.example.session;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.lightj.example.dal.SampleDatabaseEnum;
import org.lightj.initialization.BaseModule;
import org.lightj.initialization.InitializationProcessor;
import org.lightj.locking.LockManagerImpl;
import org.lightj.session.FlowDefinition;
import org.lightj.session.FlowModule;
import org.lightj.session.FlowProperties;
import org.lightj.session.FlowResult;
import org.lightj.session.FlowSaveException;
import org.lightj.session.FlowSession;
import org.lightj.session.FlowSessionFactory;
import org.lightj.session.FlowState;
import org.lightj.session.FlowStepProperties;
import org.lightj.session.step.IFlowStep;
import org.lightj.session.step.StepBuilder;
import org.lightj.session.step.StepTransition;
import org.lightj.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@FlowDefinition(typeId="Skeleton", desc="Bare minimal of a flow", group="Group_LowPriority")
@FlowProperties(clustered=false, interruptible=false, lockTarget=false, priority=5, timeoutInSec=0, killNonRecoverable=true)
@SuppressWarnings("rawtypes")
public class SkeletonFlow extends FlowSession<SkeletonFlowContext> {

	/**
	 * flow steps
	 * @author biyu
	 *
	 */
	public static enum SkeletonSteps {
		@FlowStepProperties(stepWeight=1, logging=false, onSuccess="stop", onElse="handleError", onException="handleError")
		start,
		@FlowStepProperties(stepWeight=5)
		stop,
		@FlowStepProperties(stepWeight=0, isErrorStep=true)
		handleError
	}
	
	@Override
	protected Enum getFirstStepEnum() {
		return SkeletonSteps.start;
	}

	//////////////// step implementation /////////////////
	@Autowired
	private IFlowStep skeletonStartStep;
	@Autowired
	private IFlowStep skeletonStopStep;
	@Autowired
	private IFlowStep skeletonErrorStep;
	
	public IFlowStep getSkeletonStartStep() {
		return skeletonStartStep;
	}
	public void setSkeletonStartStep(IFlowStep skeletonStartStep) {
		this.skeletonStartStep = skeletonStartStep;
	}
	public IFlowStep getSkeletonStopStep() {
		return skeletonStopStep;
	}
	public void setSkeletonStopStep(IFlowStep skeletonStopStep) {
		this.skeletonStopStep = skeletonStopStep;
	}
	public IFlowStep getSkeletonErrorStep() {
		return skeletonErrorStep;
	}
	public void setSkeletonErrorStep(IFlowStep skeletonErrorStep) {
		this.skeletonErrorStep = skeletonErrorStep;
	}
	// method with the same name as in flow step enum, framework will use reflection to run each step
	public IFlowStep start() {
		return skeletonStartStep;
	}
	public IFlowStep stop() {
		return skeletonStopStep;
	}
	public IFlowStep handleError() {
		return skeletonErrorStep;
	}
	
	public static @Bean IFlowStep skeletonStartStep() {
		return new StepBuilder().runTo(SkeletonSteps.stop).getFlowStep();
	}
	
	public static @Bean IFlowStep skeletonStopStep() {
		return new StepBuilder().parkInState(StepTransition.parkInState(FlowState.Completed, FlowResult.Success, null)).getFlowStep();
	}

	public static @Bean IFlowStep skeletonErrorStep() {
		return new StepBuilder().parkInState(StepTransition.parkInState(FlowState.Completed, FlowResult.Failed, "something wrong")).getFlowStep();
	}

	public static void main(String[] args) {
		// initialize flow framework
		ApplicationContext flowCtx = new ClassPathXmlApplicationContext("config/org/lightj/session/context-flow.xml", "config/org/lightj/session/context-examples-flow.xml");
		InitializationProcessor initializer = new InitializationProcessor(
			new BaseModule[] {
				new FlowModule().setDb(SampleDatabaseEnum.FLOW_MONGO)
								.enableCluster(new LockManagerImpl(SampleDatabaseEnum.FLOW_MONGO))
								.setSpringContext(flowCtx)
								.setExectuorService(Executors.newFixedThreadPool(5))
								.getModule()
		});
		initializer.initialize();
		
		// create an instance of skeleton flow, fill in the flesh for each steps
		SkeletonFlow flow = FlowSessionFactory.getInstance().createSession(SkeletonFlow.class);
		
		// persist the flow
		try {
			flow.save();
		} catch (FlowSaveException e) {
			e.printStackTrace();
		}
		
		// kick off flow
		flow.runFlow();
		
		// checking flow state and print progress
		while (!flow.getState().isComplete()) {
			System.out.println(flow.getFlowInfo().getProgress());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// print complete flow info and flow execution logs
		try {
			System.out.println(JsonUtil.encode(flow.getFlowInfo()));
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
}
