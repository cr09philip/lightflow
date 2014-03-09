package org.lightj.task;

import java.util.List;

import org.lightj.session.FlowContext;

@SuppressWarnings("rawtypes")
public abstract class GroupTask<T extends FlowContext> extends ExecutableTask<T> {

	/** create new instance of a task */
	public abstract <E extends ExecutableTask> E createTaskInstance();
	
	/** fan out to list of tasks */
	public abstract List<ExecutableTask> getTasks();

	@Override
	public final TaskResult execute() throws TaskExecutionException {
		throw new TaskExecutionException("not supported");
	}
}
