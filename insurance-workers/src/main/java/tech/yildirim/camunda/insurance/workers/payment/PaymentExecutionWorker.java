package tech.yildirim.camunda.insurance.workers.payment;

import java.math.BigDecimal;
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
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * Camunda external task worker responsible for executing approved insurance payments.
 *
 * <p>This worker processes payment execution tasks by updating claims with the approved payment
 * amount and changing their status to PAID. It handles all types of insurance claims (Auto, Home,
 * Health) and ensures that the payment is properly recorded in the system.
 *
 * <p>The worker subscribes to the topic "insurance.payment.execute" and expects the following
 * process variables:
 *
 * <ul>
 *   <li>{@code CLAIM_ID} - The unique identifier of the claim to process payment for
 *   <li>{@code CLAIM_TYPE} - The type of claim (AUTO, HOME, or HEALTH)
 *   <li>{@code APPROVED_AMOUNT} - The approved payment amount to be paid out
 * </ul>
 *
 * <p>Upon successful processing, the worker:
 *
 * <ul>
 *   <li>Updates the claim with the paid amount
 *   <li>Sets the claim status to PAID
 *   <li>Completes the external task with payment confirmation
 * </ul>
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentExecutionWorker extends AbstractCamundaWorker {

  private static final String TOPIC_NAME = "insurance.payment.execute";

  private final ClaimService claimService;

  /**
   * Executes the business logic for processing insurance claim payments.
   *
   * <p>This method processes the external task by:
   *
   * <ol>
   *   <li>Extracting claim information and approved amount from process variables
   *   <li>Validating the input parameters
   *   <li>Retrieving the appropriate claim based on its type
   *   <li>Updating the claim with paid amount and PAID status
   *   <li>Completing the task with payment confirmation
   * </ol>
   *
   * @param externalTask the external task containing process variables
   * @param externalTaskService service for task completion and error handling
   * @throws IllegalArgumentException if any required parameter is invalid
   * @throws IllegalStateException if claim retrieval or update fails
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    log.info("Starting payment execution for task: {}", externalTask.getId());

    // Extract and validate process variables
    Long claimId = extractAndValidateClaimId(externalTask);
    String claimTypeString = extractAndValidateClaimType(externalTask);
    BigDecimal approvedAmount = extractAndValidateApprovedAmount(externalTask);

    ClaimType claimType;
    try {
      claimType = ClaimType.valueOf(claimTypeString.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Invalid claim type: " + claimTypeString + ". Supported types are: AUTO, HOME, HEALTH",
          ex);
    }

    log.debug(
        "Processing payment execution - Claim ID: {}, Type: {}, Approved Amount: {}",
        claimId,
        claimType,
        approvedAmount);

    // Process payment based on claim type
    processPayment(claimId, claimType, approvedAmount);

    log.info(
        "Successfully executed payment of {} for claim ID: {} of type: {}",
        approvedAmount,
        claimId,
        claimType);

    // Complete task with payment confirmation
    completeTask(
        externalTask,
        externalTaskService,
        Map.of(
            "payment_executed", true,
            "paid_amount", approvedAmount,
            "payment_status", "COMPLETED"));

    log.info("Successfully completed payment execution for task: {}", externalTask.getId());
  }

  /**
   * Processes the payment for a specific claim type.
   *
   * @param claimId the ID of the claim to process payment for
   * @param claimType the type of the claim
   * @param approvedAmount the approved payment amount
   */
  private void processPayment(Long claimId, ClaimType claimType, BigDecimal approvedAmount) {
    switch (claimType) {
      case AUTO -> processAutoClaimPayment(claimId, approvedAmount);
      case HOME -> processHomeClaimPayment(claimId, approvedAmount);
      case HEALTH -> processHealthClaimPayment(claimId, approvedAmount);
    }
  }

  /**
   * Processes payment for an auto claim.
   *
   * @param claimId the auto claim ID
   * @param approvedAmount the approved payment amount
   */
  private void processAutoClaimPayment(Long claimId, BigDecimal approvedAmount) {
    AutoClaimDto claim = claimService.getAutoClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Auto claim not found with ID: " + claimId);
    }

    claim.setPaidAmount(approvedAmount);
    claim.setStatus(StatusEnum.PAID);

    AutoClaimDto updatedClaim = claimService.updateAutoClaim(claimId, claim);
    if (updatedClaim == null) {
      throw new IllegalStateException("Failed to update auto claim payment for ID: " + claimId);
    }

    log.debug("Successfully processed payment for auto claim ID: {}", claimId);
  }

  /**
   * Processes payment for a home claim.
   *
   * @param claimId the home claim ID
   * @param approvedAmount the approved payment amount
   */
  private void processHomeClaimPayment(Long claimId, BigDecimal approvedAmount) {
    HomeClaimDto claim = claimService.getHomeClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Home claim not found with ID: " + claimId);
    }

    claim.setPaidAmount(approvedAmount);
    claim.setStatus(StatusEnum.PAID);

    HomeClaimDto updatedClaim = claimService.updateHomeClaim(claimId, claim);
    if (updatedClaim == null) {
      throw new IllegalStateException("Failed to update home claim payment for ID: " + claimId);
    }

    log.debug("Successfully processed payment for home claim ID: {}", claimId);
  }

  /**
   * Processes payment for a health claim.
   *
   * @param claimId the health claim ID
   * @param approvedAmount the approved payment amount
   */
  private void processHealthClaimPayment(Long claimId, BigDecimal approvedAmount) {
    HealthClaimDto claim = claimService.getHealthClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Health claim not found with ID: " + claimId);
    }

    claim.setPaidAmount(approvedAmount);
    claim.setStatus(StatusEnum.PAID);

    HealthClaimDto updatedClaim = claimService.updateHealthClaim(claimId, claim);
    if (updatedClaim == null) {
      throw new IllegalStateException("Failed to update health claim payment for ID: " + claimId);
    }

    log.debug("Successfully processed payment for health claim ID: {}", claimId);
  }

  /**
   * Extracts and validates the claim ID from the external task.
   *
   * @param externalTask the external task
   * @return the validated claim ID
   * @throws IllegalArgumentException if claim ID is invalid
   */
  private Long extractAndValidateClaimId(ExternalTask externalTask) {
    Long claimId = externalTask.getVariable(ProcInstVars.CLAIM_ID);
    if (claimId == null) {
      throw new IllegalArgumentException("Claim ID cannot be null");
    }
    if (claimId <= 0) {
      throw new IllegalArgumentException("Claim ID must be greater than zero, but was: " + claimId);
    }
    return claimId;
  }

  /**
   * Extracts and validates the claim type from the external task.
   *
   * @param externalTask the external task
   * @return the validated claim type string
   * @throws IllegalArgumentException if claim type is invalid
   */
  private String extractAndValidateClaimType(ExternalTask externalTask) {
    String claimType = externalTask.getVariable(ProcInstVars.CLAIM_TYPE);
    if (claimType == null || claimType.trim().isEmpty()) {
      throw new IllegalArgumentException("Claim type cannot be null or empty");
    }
    return claimType;
  }

  /**
   * Extracts and validates the approved amount from the external task.
   *
   * @param externalTask the external task
   * @return the validated approved amount
   * @throws IllegalArgumentException if approved amount is invalid
   */
  private BigDecimal extractAndValidateApprovedAmount(ExternalTask externalTask) {
    BigDecimal approvedAmount = externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT);
    if (approvedAmount == null) {
      throw new IllegalArgumentException("Approved amount cannot be null");
    }
    if (approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Approved amount must be greater than zero, but was: " + approvedAmount);
    }
    return approvedAmount;
  }

  /**
   * Returns the topic name that this worker subscribes to.
   *
   * @return the topic name for payment execution tasks
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
