package tech.yildirim.camunda.insurance.workers.adjuster;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * External task worker responsible for assigning an adjuster to a claim.
 *
 * <p>This worker subscribes to the {@link #TOPIC_NAME} external task topic and delegates the actual
 * assignment logic to {@link AdjusterService}. The assigned adjuster id is written back to the
 * process instance using the process variable name defined in {@link ProcInstVars}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssignAdjusterWorker extends AbstractCamundaWorker {

  /** External task topic this worker subscribes to. */
  private static final String TOPIC_NAME = "insurance.claim.assign-adjuster";

  /** Service that performs adjuster assignment business logic. */
  private final AdjusterService adjusterService;

  /**
   * Executes the business logic for assigning an adjuster to the claim referenced by the external
   * task variables. The method reads the claim id and claim type from the process variables,
   * delegates the assignment to {@link AdjusterService} and completes the task with the assigned
   * adjuster id as a process variable.
   *
   * @param externalTask the external task providing input variables
   * @param externalTaskService the external task service used to complete the task
   * @throws IllegalArgumentException if required process variables are missing or invalid
   * @throws IllegalStateException if the adjuster assignment failed or returned invalid data
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    // Extract and validate process variables
    Long claimId = externalTask.getVariable(ProcInstVars.CLAIM_ID);
    String claimTypeString = externalTask.getVariable(ProcInstVars.CLAIM_TYPE);

    if (claimId == null) {
      throw new IllegalArgumentException(
          "Process variable '" + ProcInstVars.CLAIM_ID + "' is required");
    }
    if (claimTypeString == null || claimTypeString.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "Process variable '" + ProcInstVars.CLAIM_TYPE + "' is required");
    }

    final ClaimType claimType;
    try {
      claimType = ClaimType.valueOf(claimTypeString.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid claim type: " + claimTypeString, e);
    }

    // Delegate assignment to service
    ClaimDto claimDto = adjusterService.assignAdjuster(claimType, claimId);

    // Validate service response
    if (claimDto == null || claimDto.getAssignedAdjusterId() == null) {
      log.error("Adjuster assignment failed for claimId={}, response={}", claimId, claimDto);
      throw new IllegalStateException("Adjuster assignment failed for claim: " + claimId);
    }

    // Complete the external task with the assigned adjuster id
    completeTask(
        externalTask,
        externalTaskService,
        Map.of(ProcInstVars.ADJUSTER_ID, claimDto.getAssignedAdjusterId()));

    log.info("Assigned adjuster {} to claim {}", claimDto.getAssignedAdjusterId(), claimId);
  }

  /**
   * Returns the external task topic name this worker subscribes to.
   *
   * @return external task topic name
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
