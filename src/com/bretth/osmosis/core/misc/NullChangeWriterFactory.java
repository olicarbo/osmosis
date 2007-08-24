package com.bretth.osmosis.core.misc;

import java.util.Map;

import com.bretth.osmosis.core.pipeline.ChangeSinkManager;
import com.bretth.osmosis.core.pipeline.TaskManager;
import com.bretth.osmosis.core.pipeline.TaskManagerFactory;


/**
 * The task manager factory for a null change writer.
 * 
 * @author Brett Henderson
 */
public class NullChangeWriterFactory extends TaskManagerFactory {
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TaskManager createTaskManagerImpl(String taskId, Map<String, String> taskArgs, Map<String, String> pipeArgs) {
		return new ChangeSinkManager(taskId, new NullChangeWriter(), pipeArgs);
	}
}