package tech.yildirim.camunda.insurance.workers.common;

import org.camunda.bpm.client.task.ExternalTaskHandler;

/**
 * Common interface for all Camunda External Task workers in the insurance domain.
 * Implementations of this interface are automatically discovered by Spring and registered
 * with the External Task Client for processing tasks from specific topics.
 *
 * <p>This interface extends {@link ExternalTaskHandler} to provide the core task handling
 * functionality while adding domain-specific methods for topic identification.</p>
 */
public interface CamundaWorker extends ExternalTaskHandler {

  /**
   * Returns the topic name that this worker should subscribe to for processing external tasks.
   * The topic name should match the topic configured in the BPMN process definition.
   *
   * @return the topic name this worker handles
   */
  String getTopicName();
}
