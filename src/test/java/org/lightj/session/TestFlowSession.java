package org.lightj.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.framework.Assert;

import org.junit.Test;
import org.lightj.BaseTestCase;
import org.lightj.example.dal.SampleDatabaseEnum;
import org.lightj.example.session.helloworld.HelloWorldFlow;
import org.lightj.example.session.helloworld.HelloWorldFlowEventListener;
import org.lightj.example.session.simplehttpflow.HttpTaskUtil.HttpTaskWrapper;
import org.lightj.example.session.simplehttpflow.SimpleHttpFlow;
import org.lightj.initialization.BaseModule;
import org.lightj.initialization.InitializationException;
import org.lightj.initialization.ShutdownException;
import org.lightj.task.ExecuteOption;
import org.lightj.task.MonitorOption;
import org.lightj.task.asynchttp.UrlTemplate;
import org.lightj.util.ConcurrentUtil;
import org.lightj.util.JsonUtil;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


public class TestFlowSession extends BaseTestCase {

	/** pause lock */
	protected ReentrantLock lock = new ReentrantLock();
	
	/** pause condition */
	protected Condition cond = lock.newCondition();
	
	@Test
	public void testSimpleHttpFlow() throws Exception {
		// create an instance of skeleton flow, fill in the flesh for each steps
		SimpleHttpFlow flow = FlowSessionFactory.getInstance().createSession(SimpleHttpFlow.class);
		String[] sites = new String[] {"www.yahoo.com","www.facebook.com"};
		for (int i = 0 ; i < 2; i++) {
			HttpTaskWrapper tw = new HttpTaskWrapper();
			tw.setTaskType("async");
			tw.setHttpClientType("httpClient");
			tw.setExecutionOption(new ExecuteOption());
			tw.setUrlTemplate(new UrlTemplate("https://#host"));
			HashMap<String, String> tv = new HashMap<String, String>();
			tv.put("#host", sites[i]);
			tw.setTemplateValues(tv);
			flow.getSessionContext().addHttpTask(tw);
		}
		
		HttpTaskWrapper tw1 = new HttpTaskWrapper();
		tw1.setTaskType("asyncpull");
		tw1.setHttpClientType("httpClient");
		tw1.setExecutionOption(new ExecuteOption());
		tw1.setUrlTemplate(new UrlTemplate("https://#host"));
		HashMap<String, String> tv = new HashMap<String, String>();
		tv.put("#host", "www.yahoo.com");
		tw1.setTemplateValues(tv);

		tw1.setMonitorOption(new MonitorOption(1000, 5000));
		tw1.setPullTemplate(new UrlTemplate("https://#host"));
		ArrayList<String> transferV = new ArrayList<String>();
		transferV.add("#host");
		tw1.setTransferableVariables(transferV);

		flow.getSessionContext().addHttpTask(tw1);
		
		flow.save();
		// kick off flow
		flow.runFlow();
		
		// checking flow state and print progress
		while (!flow.getState().isComplete()) {
			System.out.println(flow.getFlowInfo().getProgress());
			Thread.sleep(1000);
		}
		System.out.println(JsonUtil.encode(flow.getFlowInfo()));
	}

	@Test
	public void testHelloWorld() throws Exception {
		HelloWorldFlow session = FlowSessionFactory.getInstance().createSession(HelloWorldFlow.class);
		session.getSessionContext().setGoodHosts("www.yahoo.com", "www.yahoo.com");
		session.save();
		session.addEventListener(new HelloWorldFlowEventListener(lock, cond));
		session.runFlow();
		ConcurrentUtil.wait(lock, cond, 30 * 1000);
		System.out.println(JsonUtil.encode(session.getFlowInfo()));
		Assert.assertEquals(1, session.getSessionContext().getTaskCount());
		Assert.assertEquals(2, session.getSessionContext().getSplitCount());
		Assert.assertEquals(2, session.getSessionContext().getRetryCount());
		Assert.assertEquals(1, session.getSessionContext().getTimeoutCount());
		Assert.assertEquals(10, session.getSessionContext().getBatchCount());
		Assert.assertEquals(0, session.getSessionContext().getErrorStepCount());
		
	}

//	@Test
//	public void testHelloWorldFailureRuntime() throws Exception {
//		HelloWorldFlow session = FlowSessionFactory.getInstance().createSession(HelloWorldFlow.class);
//		session.getSessionContext().setInjectFailure(true);
//		session.getSessionContext().setControlledFailure(false);
//		
//		// use DI to set step impl
//		session.save();
//		session.addEventListener(new HelloWorldFlowEventListener(lock, cond));
//		session.runFlow();
//		ConcurrentUtil.wait(lock, cond);
//		Assert.assertEquals(1, session.getSessionContext().getErrorStepCount());
//		Assert.assertEquals("testFailureStep", session.getCurrentAction());
//		Assert.assertEquals(FlowResult.Failed, session.getResult());
//		Assert.assertEquals(FlowState.Completed, session.getState());
//		System.out.println(new ObjectMapper().writeValueAsString(session.getSessionContext().getLastErrors()));
//	}
//	
//	@Test
//	public void testHelloWorldFailureControlled() throws Exception {
//		HelloWorldFlow session = FlowSessionFactory.getInstance().createSession(HelloWorldFlow.class);
//		session.getSessionContext().setInjectFailure(true);
//		
//		// use DI to set step impl
//		session.save();
//		session.addEventListener(new HelloWorldFlowEventListener(lock, cond));
//		session.runFlow();
//		ConcurrentUtil.wait(lock, cond);
//		Assert.assertEquals(1, session.getSessionContext().getErrorStepCount());
//		Assert.assertEquals("testFailureStep", session.getCurrentAction());
//		Assert.assertEquals(FlowResult.Failed, session.getResult());
//		Assert.assertEquals(FlowState.Completed, session.getState());
//		System.out.println(new ObjectMapper().writeValueAsString(session.getSessionContext().getLastErrors()));
//	}
//
//	@Test
//	public void testPauseResume() throws Exception {
//		HelloWorldFlow session = FlowSessionFactory.getInstance().createSession(HelloWorldFlow.class);
//		session.getSessionContext().setInjectFailure(true);
//		session.getSessionContext().setControlledFailure(false);
//		// pause on error
//		session.getSessionContext().setPauseOnError(true);
//		// use DI to set step impl
//		session.save();
//		session.addEventListener(new HelloWorldFlowEventListener(lock, cond));
//		session.runFlow();
//		ConcurrentUtil.wait(lock, cond);
//		Assert.assertEquals("testFailureStep", session.getCurrentAction());
//		Assert.assertEquals(1, session.getSessionContext().getErrorStepCount());
//		Assert.assertEquals(FlowResult.Failed, session.getResult());
//		Assert.assertEquals(FlowState.Paused, session.getState());
//		
//		// reset current step to the desirable step and resume flow
//		HelloWorldFlow session1 = (HelloWorldFlow) FlowSessionFactory.getInstance().findByKey(session.getKey());
//		session1.setCurrentAction("testFailureStep");
//		// don't pause this time
//		session1.getSessionContext().setPauseOnError(false);
//		session1.addEventListener(new HelloWorldFlowEventListener(lock, cond));
//		session1.runFlow();
//		ConcurrentUtil.wait(lock, cond);
//		
//		System.out.println(new ObjectMapper().writeValueAsString(session1.getFlowInfo()));
//
//		// second time we did not set private error flag in context, so flow did not go to the error step
//		Assert.assertEquals(1, session1.getSessionContext().getErrorStepCount());
//		Assert.assertEquals("stop", session1.getCurrentAction());
//		Assert.assertEquals(FlowResult.Success, session1.getResult());
//		Assert.assertEquals(FlowState.Completed, session1.getState());
//		
//	}
	

	@Override
	protected void afterInitialize(String home) throws InitializationException {
	}

	@Override
	protected void afterShutdown() throws ShutdownException {
	}

	@Override
	protected BaseModule[] getDependentModules() {
		AnnotationConfigApplicationContext flowCtx = new AnnotationConfigApplicationContext("org.lightj.example");
		return new BaseModule[] {
				new FlowModule().setDb(SampleDatabaseEnum.TEST)
								.enableCluster()
								.setSpringContext(flowCtx)
								.setExectuorService(Executors.newFixedThreadPool(5))
								.getModule(),
		};
	}
}
