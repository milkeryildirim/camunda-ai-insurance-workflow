package tech.yildirim.camunda.insurance.workers.policy;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;

import java.util.Map;

/**
 * External task worker responsible for validating insurance policies.
 * This worker handles policy validation tasks from the Camunda process engine
 * and determines whether a policy is valid for claim processing.
 *
 * <p>The worker subscribes to the "policy-validation" topic and processes
 * external tasks by checking policy status, coverage, and validity dates.</p>
 */
@Component
@Slf4j
public class PolicyValidationWorker extends AbstractCamundaWorker {

  private static final String TOPIC_NAME = "insurance.claim.policy-validate";
  private static final String POLICY_VALID_VAR = "is_policy_valid";
  private static final String POLICY_NUMBER_VAR = "policy_number";

  /**
   * Returns the topic name this worker subscribes to.
   *
   * @return the topic name "policy-validation"
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  /**
   * Executes the policy validation business logic.
   * Validates the insurance policy and sets the validation result
   * as a process variable for the workflow to continue.
   *
   * @param externalTask the external task containing policy information
   * @param externalTaskService service to complete the task
   * @throws IllegalArgumentException if policy number is invalid
   */
  @Override
  protected void executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService)
      throws IllegalArgumentException {

    String policyNumber = externalTask.getVariable(POLICY_NUMBER_VAR);
    log.info("Validating policy number: {}", policyNumber);

    boolean isPolicyValid = validatePolicy(policyNumber);

    completeTask(externalTask, externalTaskService,
        Map.of(POLICY_VALID_VAR, isPolicyValid));

    log.info("Policy validation result for {}: {}",
             policyNumber, isPolicyValid ? "VALID" : "INVALID");
  }

  /**
   * Validates the given policy number by checking various policy criteria.
   * This is a simplified implementation that can be extended with real
   * policy validation logic, database lookups, or external service calls.
   *
   * @param policyNumber the policy number to validate
   * @return true if the policy is valid, false otherwise
   * @throws IllegalArgumentException if policy number is null or empty
   */
  private boolean validatePolicy(String policyNumber) throws IllegalArgumentException {
    if (policyNumber == null || policyNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("Policy number cannot be null or empty");
    }

    log.debug("Validating policy number format: {}", policyNumber);

    return false;
  }
}
