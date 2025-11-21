package tech.yildirim.camunda.insurance.workers.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * Abstract base class for payment calculation workers.
 *
 * <p>This abstract worker provides the common functionality for calculating payment amounts based
 * on invoice amounts and different percentage rates. Concrete implementations define the specific
 * percentage to be applied and the topic name for subscription.
 *
 * <p>The worker extracts the invoice amount from process variables and calculates the payment
 * amount by applying a percentage rate. The calculated amount is then set as a process variable
 * for further processing in the workflow.
 *
 * <p>Expected process variables:
 *
 * <ul>
 *   <li>{@code INVOICE_AMOUNT} - The original invoice amount to calculate payment from
 * </ul>
 *
 * <p>Output process variables:
 *
 * <ul>
 *   <li>{@code APPROVED_AMOUNT} - The calculated payment amount based on the percentage
 * </ul>
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractPaymentCalculationWorker extends AbstractCamundaWorker {

  private final ClaimService claimService;

  protected AbstractPaymentCalculationWorker(ClaimService claimService){
    this.claimService = claimService;
  }

  /**
   * Executes the business logic for calculating payment amounts.
   *
   * <p>This method processes the external task by:
   *
   * <ol>
   *   <li>Extracting the invoice amount from process variables
   *   <li>Validating the invoice amount
   *   <li>Calculating the payment amount using the concrete implementation's percentage
   *   <li>Completing the task with the calculated approved amount
   * </ol>
   *
   * @param externalTask the external task containing process variables
   * @param externalTaskService service for task completion and error handling
   * @throws IllegalArgumentException if invoice amount is null, negative, or zero
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    log.info("Starting payment calculation for task: {}", externalTask.getId());

    // Extract invoice amount from process variables
    BigDecimal invoiceAmount = extractInvoiceAmount(externalTask);

    log.debug(
        "Processing payment calculation - Invoice Amount: {}, Percentage: {}%",
        invoiceAmount,
        getPaymentPercentage());

    // Calculate approved amount based on percentage
    BigDecimal approvedAmount = calculatePaymentAmount(invoiceAmount);

    // Update claim decision with approved amount
    updateClaimDecision(externalTask, approvedAmount);

    log.info(
        "Calculated approved amount: {} ({}% of {})",
        approvedAmount,
        getPaymentPercentage(),
        invoiceAmount);

    // Complete task with calculated amount
    completeTask(
        externalTask,
        externalTaskService,
        Map.of(ProcInstVars.APPROVED_AMOUNT, approvedAmount));

    log.info("Successfully completed payment calculation for task: {}", externalTask.getId());
  }

  /**
   * Updates the claim decision with the approved payment amount.
   *
   * @param externalTask the external task containing process variables
   * @param approvedAmount the calculated approved amount
   */
  private void updateClaimDecision(ExternalTask externalTask, BigDecimal approvedAmount) {
    // Extract claim information for updating decision
    Long claimId = externalTask.getVariable(ProcInstVars.CLAIM_ID);
    String claimTypeString = externalTask.getVariable(ProcInstVars.CLAIM_TYPE);

    if (claimId == null) {
      throw new IllegalArgumentException("Claim ID cannot be null");
    }

    if (claimTypeString == null || claimTypeString.trim().isEmpty()) {
      throw new IllegalArgumentException("Claim type cannot be null or empty");
    }

    ClaimType claimType;
    try {
      claimType = ClaimType.valueOf(claimTypeString.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Invalid claim type: " + claimTypeString + ". Supported types are: AUTO, HOME, HEALTH", ex);
    }

    switch (claimType) {
      case AUTO -> updateAutoClaimDecision(claimId, approvedAmount, externalTask);
      case HEALTH -> updateHealthClaimDecision(claimId, approvedAmount, externalTask);
      case HOME -> updateHomeClaimDecision(claimId, approvedAmount, externalTask);
    }
  }

  /**
   * Updates the decision for an auto claim.
   *
   * @param claimId the claim ID
   * @param approvedAmount the approved payment amount
   * @param externalTask the external task for additional data
   */
  private void updateAutoClaimDecision(Long claimId, BigDecimal approvedAmount, ExternalTask externalTask) {
    AutoClaimDto claim = claimService.getAutoClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Auto claim not found with ID: " + claimId);
    }
    ClaimDecisionDto claimDecisionDto = (ClaimDecisionDto) claim.getClaimDecision();
    if (claimDecisionDto == null) {
      throw new IllegalStateException("Claim decision not found for auto claim ID: " + claimId);
    }
    claimDecisionDto.setApprovedAmount(approvedAmount);
    claimDecisionDto.setUpdatedAt(OffsetDateTime.now());
    claimDecisionDto.setAdditionalNotes(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS));
    claimService.updateClaimDecision(claimDecisionDto, ClaimType.AUTO);
  }

  /**
   * Updates the decision for a health claim.
   *
   * @param claimId the claim ID
   * @param approvedAmount the approved payment amount
   * @param externalTask the external task for additional data
   */
  private void updateHealthClaimDecision(Long claimId, BigDecimal approvedAmount, ExternalTask externalTask) {
    HealthClaimDto claim = claimService.getHealthClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Health claim not found with ID: " + claimId);
    }
    ClaimDecisionDto claimDecisionDto = (ClaimDecisionDto) claim.getClaimDecision();
    if (claimDecisionDto == null) {
      throw new IllegalStateException("Claim decision not found for health claim ID: " + claimId);
    }
    claimDecisionDto.setApprovedAmount(approvedAmount);
    claimDecisionDto.setUpdatedAt(OffsetDateTime.now());
    claimDecisionDto.setAdditionalNotes(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS));
    claimService.updateClaimDecision(claimDecisionDto, ClaimType.HEALTH);
  }

  /**
   * Updates the decision for a home claim.
   *
   * @param claimId the claim ID
   * @param approvedAmount the approved payment amount
   * @param externalTask the external task for additional data
   */
  private void updateHomeClaimDecision(Long claimId, BigDecimal approvedAmount, ExternalTask externalTask) {
    HomeClaimDto claim = claimService.getHomeClaimById(claimId);
    if (claim == null) {
      throw new IllegalStateException("Home claim not found with ID: " + claimId);
    }
    ClaimDecisionDto claimDecisionDto = (ClaimDecisionDto) claim.getClaimDecision();
    if (claimDecisionDto == null) {
      throw new IllegalStateException("Claim decision not found for home claim ID: " + claimId);
    }
    claimDecisionDto.setApprovedAmount(approvedAmount);
    claimDecisionDto.setUpdatedAt(OffsetDateTime.now());
    claimDecisionDto.setAdditionalNotes(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS));
    claimService.updateClaimDecision(claimDecisionDto, ClaimType.HOME);
  }

  /**
   * Extracts and validates the invoice amount from the external task.
   *
   * @param externalTask the external task containing process variables
   * @return the validated invoice amount
   * @throws IllegalArgumentException if invoice amount is invalid
   */
  private BigDecimal extractInvoiceAmount(ExternalTask externalTask) {
    BigDecimal invoiceAmount = externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT);

    if (invoiceAmount == null) {
      throw new IllegalArgumentException("Invoice amount cannot be null");
    }

    if (invoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Invoice amount must be greater than zero, but was: " + invoiceAmount);
    }

    return invoiceAmount;
  }

  /**
   * Calculates the payment amount based on the invoice amount and percentage.
   *
   * @param invoiceAmount the original invoice amount
   * @return the calculated payment amount
   */
  private BigDecimal calculatePaymentAmount(BigDecimal invoiceAmount) {
    BigDecimal percentage = BigDecimal.valueOf(getPaymentPercentage());
    return invoiceAmount
        .multiply(percentage)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /**
   * Returns the payment percentage to be applied by the concrete implementation.
   *
   * @return the payment percentage (e.g., 80 for 80%, 100 for 100%)
   */
  protected abstract int getPaymentPercentage();
}
