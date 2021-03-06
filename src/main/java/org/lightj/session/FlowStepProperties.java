package org.lightj.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * flow step properties annotation
 * 
 * @author binyu
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowStepProperties {
	
	/**
	 * error handling step, 
	 * flow driver will execute the step without setting Current Action to it
	 * @return
	 */
	boolean isErrorStep()	default false;
	
	/**
	 * first step to run,
	 * flow drive will execute the step without setting when flow start from scratch
	 * @return
	 */
	boolean isFirstStep()	default false;
	
	/**
	 * step description
	 * @return
	 */
	String desc()	default "";
	
	/**
	 * order of the steps, start step idx=0, erro step idx=Integer.max
	 * @return
	 */
	int stepIdx();
	
	/**
	 * relative weight of the step, used to calculate flow progress
	 * @return
	 */
	int stepWeight() 	default 1;
	
	/**
	 * setting this property to true will force flow driver
	 * to persist logs of a flow step execution to session step log
	 * at end of a flow step execution
	 * @return
	 */
	boolean logging()	default true;
	
	/**
	 * step to go to on success
	 * @return
	 */
	String onSuccess()	default "";
	
	/**
	 * step to go to on failure and other non-success
	 * @return
	 */
	String onElse()	default "";
	
	/**
	 * step to go to on exception
	 * @return
	 */
	String onException() default "";
	
}

