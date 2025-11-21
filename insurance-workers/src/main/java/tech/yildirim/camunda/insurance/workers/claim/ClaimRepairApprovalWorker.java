package tech.yildirim.camunda.insurance.workers.claim;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto.StatusEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * Camunda external task worker responsible for approving insurance claim repairs.
 *
 * <p>This worker processes repair approval tasks for different types of insurance claims by:
 *
 * <ul>
 *   <li>Retrieving the claim information based on claim ID and type
 *   <li>Updating the claim status to APPROVED
 *   <li>Completing the external task with success confirmation
 * </ul>
 *
 * <p>The worker subscribes to the topic "insurance.claim.repair-approve" and expects the following
 * process variables:
 *
 * <ul>
 *   <li>{@code CLAIM_ID} - The unique identifier of the claim to approve
 *   <li>{@code CLAIM_TYPE} - The type of claim (AUTO, HOME, or HEALTH)
 * </ul>
 *
 * <p>Upon successful processing, the worker sets the repair approval status in the process
 * variables to indicate completion.
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClaimRepairApprovalWorker extends AbstractCamundaWorker {

  private static final String TOPIC_NAME = "insurance.claim.repair-approve";

  private final ClaimService claimService;

  /**
   * Executes the business logic for approving insurance claim repairs.
   *
   * <p>This method processes the external task by:
   *
   * <ol>
   *   <li>Extracting claim ID and type from task variables
   *   <li>Retrieving the appropriate claim based on its type
   *   <li>Updating the claim status to APPROVED
   *   <li>Completing the task with success confirmation
   * </ol>
   *
   * @param externalTask the external task containing process variables
   * @param externalTaskService service for task completion and error handling
   * @throws IllegalStateException if claim retrieval or update fails
   * @throws IllegalArgumentException if an invalid claim type is provided
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    log.info("Starting repair approval for task: {}", externalTask.getId());

    // Extract process variables
    Long claimId = externalTask.getVariable(ProcInstVars.CLAIM_ID);
    String claimTypeString = externalTask.getVariable(ProcInstVars.CLAIM_TYPE);

    log.debug("Processing repair approval - Claim ID: {}, Type: {}", claimId, claimTypeString);

    if (claimId == null) {
      throw new IllegalArgumentException("Claim ID cannot be null");
    }

    if (claimTypeString == null || claimTypeString.trim().isEmpty()) {
      throw new IllegalArgumentException("Claim type cannot be null or empty");
    }

    ClaimType claimType;
    try {
      claimType = ClaimType.valueOf(claimTypeString.toUpperCase().trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Invalid claim type: " + claimTypeString + ". Supported types are: AUTO, HOME, HEALTH",
          ex);
    }

    // Process claim based on type
    boolean approvalSuccess = processClaimApproval(claimId, claimType);

    if (!approvalSuccess) {
      throw new IllegalStateException(
          "Failed to approve repair for claim ID: " + claimId + " of type: " + claimType);
    }

    log.info("Successfully approved repair for claim ID: {} of type: {}", claimId, claimType);

    // Complete task with success status
    completeTask(
        externalTask,
        externalTaskService,
        Map.of("repair_approval_completed", true, "repair_approval_status", "APPROVED"));
  }

  /**
   * Processes the repair approval for a specific claim type.
   *
   * @param claimId the ID of the claim to approve
   * @param claimType the type of the claim
   * @return true if approval was successful, false otherwise
   */
  private boolean processClaimApproval(Long claimId, ClaimType claimType) {
    try {
      switch (claimType) {
        case AUTO -> {
          AutoClaimDto autoClaim = claimService.getAutoClaimById(claimId);
          if (autoClaim == null) {
            log.error("Auto claim not found with ID: {}", claimId);
            return false;
          }

          autoClaim.setStatus(StatusEnum.APPROVED);
          AutoClaimDto updatedClaim = claimService.updateAutoClaim(autoClaim.getId(), autoClaim);

          if (updatedClaim == null) {
            log.error("Failed to update auto claim status for ID: {}", claimId);
            return false;
          }

          log.debug("Successfully approved auto claim repair for ID: {}", claimId);
          return true;
        }
        case HOME -> {
          HomeClaimDto homeClaim = claimService.getHomeClaimById(claimId);
          if (homeClaim == null) {
            log.error("Home claim not found with ID: {}", claimId);
            return false;
          }

          homeClaim.setStatus(StatusEnum.APPROVED);
          HomeClaimDto updatedClaim = claimService.updateHomeClaim(homeClaim.getId(), homeClaim);

          if (updatedClaim == null) {
            log.error("Failed to update home claim status for ID: {}", claimId);
            return false;
          }

          log.debug("Successfully approved home claim repair for ID: {}", claimId);
          return true;
        }
        case HEALTH -> {
          HealthClaimDto healthClaim = claimService.getHealthClaimById(claimId);
          if (healthClaim == null) {
            log.error("Health claim not found with ID: {}", claimId);
            return false;
          }

          healthClaim.setStatus(StatusEnum.APPROVED);
          HealthClaimDto updatedClaim =
              claimService.updateHealthClaim(healthClaim.getId(), healthClaim);

          if (updatedClaim == null) {
            log.error("Failed to update health claim status for ID: {}", claimId);
            return false;
          }

          log.debug("Successfully approved health claim repair for ID: {}", claimId);
          return true;
        }
        default ->
            throw new IllegalArgumentException(
                "Unsupported claim type: " + claimType + ". Supported types are: AUTO, HOME, HEALTH");
      }
    } catch (Exception ex) {
      log.error(
          "Error occurred while processing repair approval for claim ID: {} of type: {}",
          claimId,
          claimType,
          ex);
      return false;
    }
  }

  /**
   * Returns the topic name that this worker subscribes to.
   *
   * @return the topic name for claim repair approval tasks
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
