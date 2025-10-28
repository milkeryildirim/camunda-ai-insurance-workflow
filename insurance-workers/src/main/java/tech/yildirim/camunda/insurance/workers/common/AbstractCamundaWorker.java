package tech.yildirim.camunda.insurance.workers.common;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.Map;

/**
 * Abstract base class providing common functionality for all Camunda external task workers.
 * This class implements the {@link CamundaWorker} interface and provides utility methods
 * for error handling, task completion, and logging.
 *
 * <p>Concrete implementations should extend this class and implement the
 * {@link #executeBusinessLogic(ExternalTask, ExternalTaskService)} method
 * to provide specific business logic.</p>
 */
@Slf4j
public abstract class AbstractCamundaWorker implements CamundaWorker {

  private static final int DEFAULT_RETRY_COUNT = 3;
  private static final long DEFAULT_RETRY_TIMEOUT = 5000L;

  /**
   * Template method that handles the external task execution flow.
   * This method provides common error handling and delegates the actual
   * business logic to the implementing class.
   *
   * @param externalTask the external task to process
   * @param externalTaskService service for task completion and failure handling
   */
  @Override
  public final void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    String taskId = externalTask.getId();
    String activityId = externalTask.getActivityId();
    String topicName = getTopicName();

    log.info("Starting execution of task {} for topic {} (activity: {})", taskId, topicName, activityId);

    try {
      executeBusinessLogic(externalTask, externalTaskService);
      log.info("Successfully completed task {} for topic {}", taskId, topicName);
    } catch (Exception e) {
      handleTaskFailure(externalTask, externalTaskService, e);
    }
  }

  /**
   * Executes the specific business logic for this worker.
   * Implementations should provide their domain-specific logic here.
   *
   * @param externalTask the external task containing process variables
   * @param externalTaskService service for completing tasks and setting variables
   * @throws Exception if an error occurs during business logic execution
   */
  protected abstract void executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService)
      throws Exception;

  /**
   * Completes the external task with the given process variables.
   *
   * @param externalTask the task to complete
   * @param externalTaskService the service to use for completion
   * @param variables the process variables to set
   */
  protected void completeTask(ExternalTask externalTask, ExternalTaskService externalTaskService,
                             Map<String, Object> variables) {
    externalTaskService.complete(externalTask, variables);
    log.debug("Task {} completed with variables: {}", externalTask.getId(), variables.keySet());
  }

  /**
   * Handles task failure by logging the error and reporting it to Camunda.
   *
   * @param externalTask the failed task
   * @param externalTaskService the service to report failure
   * @param exception the exception that caused the failure
   */
  private void handleTaskFailure(ExternalTask externalTask, ExternalTaskService externalTaskService, Exception exception) {
    String taskId = externalTask.getId();
    String errorMessage = exception.getMessage();

    log.error("Task {} failed with error: {}", taskId, errorMessage, exception);

    externalTaskService.handleFailure(
        externalTask,
        errorMessage,
        getDetailedErrorMessage(exception),
        getRetryCount(),
        getRetryTimeout()
    );
  }

  /**
   * Extracts a detailed error message from the exception.
   *
   * @param exception the exception to extract message from
   * @return detailed error message
   */
  private String getDetailedErrorMessage(Exception exception) {
    return exception.getClass().getSimpleName() + ": " + exception.getMessage();
  }

  /**
   * Returns the number of retries for failed tasks.
   * Can be overridden by subclasses to provide custom retry behavior.
   *
   * @return number of retries (default: 3)
   */
  protected int getRetryCount() {
    return DEFAULT_RETRY_COUNT;
  }

  /**
   * Returns the timeout between retries in milliseconds.
   * Can be overridden by subclasses to provide custom retry timing.
   *
   * @return retry timeout in milliseconds (default: 5000ms)
   */
  protected long getRetryTimeout() {
    return DEFAULT_RETRY_TIMEOUT;
  }
}
