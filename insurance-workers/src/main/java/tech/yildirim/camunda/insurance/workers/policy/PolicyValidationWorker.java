package tech.yildirim.camunda.insurance.workers.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;

import java.util.Map;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * External task worker that validates insurance policies.
 *
 * <p>This worker subscribes to the external task topic identified by {@link #TOPIC_NAME} and
 * performs basic policy validation by delegating to {@link PolicyService}. The validation result is
 * written back to the process as a process variable named by {@link ProcInstVars#POLICY_NUMBER}.
 *
 * <p>Implementation notes:
 * - This class uses the Camunda External Task client model and completes tasks using
 *   {@link AbstractCamundaWorker#completeTask(ExternalTask, ExternalTaskService, Map)}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PolicyValidationWorker extends AbstractCamundaWorker {

  /** External task topic this worker subscribes to. */
  private static final String TOPIC_NAME = "insurance.claim.policy-validate";

  private final PolicyService policyService;

  /**
   * Returns the topic name the worker subscribes to.
   *
   * @return the external task topic name
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  /**
   * Executes business logic for the external task: reads the policy number from the task,
   * validates the policy and completes the task while setting the validation result as a
   * process variable.
   *
   * @param externalTask the external task containing input variables
   * @param externalTaskService the service used to complete or handle the external task
   * @throws IllegalArgumentException when the policy number is null or empty
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService)
      throws IllegalArgumentException {

    // read and coerce the policy number variable
    final String policyNumber = externalTask.getVariable(ProcInstVars.POLICY_NUMBER);
    log.info("Validating policy number: {}", policyNumber);

    final boolean isPolicyValid = validatePolicy(policyNumber);

    // persist validation result back to the process instance
    completeTask(externalTask, externalTaskService, Map.of(ProcInstVars.IS_POLICY_VALID, isPolicyValid));

    log.info("Policy validation result for {}: {}", policyNumber, isPolicyValid ? "VALID" : "INVALID");
  }

  /**
   * Validates the provided policy number using {@link PolicyService}.
   *
   * <p>Throws {@link IllegalArgumentException} when the input is null or empty. The actual
   * validation semantics (active status, date checks, etc.) are delegated to {@link PolicyService}.
   *
   * @param policyNumber the policy number to validate (must not be null or blank)
   * @return true when the policy is considered valid, false otherwise
   * @throws IllegalArgumentException when {@code policyNumber} is null or blank
   */
  private boolean validatePolicy(String policyNumber) throws IllegalArgumentException {
    if (policyNumber == null || policyNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("Policy number cannot be null or empty");
    }

    return policyService.isPolicyValid(policyNumber);
  }
}
