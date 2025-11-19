package tech.yildirim.camunda.insurance.workers.claim;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;

/**
 * Camunda external task worker responsible for processing claim rejections based on adjuster
 * decisions.
 *
 * <p>This worker handles the business logic for rejecting insurance claims when an adjuster has
 * made a negative decision after reviewing the claim details. It performs the following operations:
 *
 * <ul>
 *   <li>Extracts and validates claim and adjuster decision information from process variables
 *   <li>Creates a rejection decision record in the system with adjuster details
 *   <li>Sends a detailed notification to the customer explaining the rejection
 *   <li>Records the decision notes and adjuster information for audit purposes
 * </ul>
 *
 * <p>The worker is triggered by the Camunda process engine when an adjuster has made a decision to
 * reject a claim. It integrates with multiple services to complete the rejection workflow
 * comprehensively, ensuring proper documentation and customer communication.
 *
 * <p>This worker extends {@link AbstractClaimRejectionWorker} to leverage common rejection
 * functionality while providing specific behavior for adjuster-based decisions.
 *
 * @author Yildirim
 * @since 1.0
 */
@Component
@Slf4j
public class ClaimRejectionDecisionWorker extends AbstractClaimRejectionWorker {

  /** The Camunda topic name that this worker subscribes to. */
  private static final String TOPIC_NAME = "insurance.claim.reject-by-decision";

  /**
   * Template message sent to customers when their claim is rejected by adjuster decision.
   * Placeholders: customer first name, last name, claim file number, policy number, decision notes.
   */
  private static final String NOTIFICATION_MESSAGE =
      """
      Dear %s %s,

      We are writing to formally inform you regarding the status of your claim (File #%s), filed under policy #%s.

      After a thorough review and assessment by our claims adjustment team, we regret to inform you that we are unable to approve your claim request at this time.

      Based on the adjuster's evaluation, the decision was made due to the following reason(s):

      -------------------------------------------------------------
      ADJUSTER'S ASSESSMENT NOTES:
      "%s"
      -------------------------------------------------------------

      If you believe this decision was made in error or if you have additional information that was not previously submitted, you have the right to appeal this decision. Please contact our Claims Department at +1-800-555-0199 referencing your claim file number.

      Sincerely,
      The Insurance Team
      """;

  /** Standard decision message recorded in the system for adjuster-based rejections. */
  private static final String DECISION_MESSAGE = "Claim rejected based on adjuster's assessment.";

  /**
   * Constructor for ClaimRejectionDecisionWorker.
   *
   * @param claimService the service for claim operations
   * @param notificationService the service for sending notifications
   * @param validator the validator for Bean Validation
   */
  public ClaimRejectionDecisionWorker(
      ClaimService claimService, NotificationService notificationService, Validator validator) {
    super(claimService, notificationService, validator);
  }

  /**
   * Executes the business logic for rejecting a claim based on adjuster's decision.
   *
   * <p>This method orchestrates the complete claim rejection workflow for adjuster decisions:
   *
   * <ol>
   *   <li>Extracts claim and adjuster decision information from process variables
   *   <li>Validates input data using Bean Validation
   *   <li>Formats and sends rejection notification to the customer with decision details
   *   <li>Creates and persists the claim decision record with adjuster information
   *   <li>Logs the processing result for audit and monitoring purposes
   * </ol>
   *
   * @param externalTask the external task containing process variables and context
   * @param externalTaskService service for completing or handling task failures
   * @throws RuntimeException if any step in the rejection process fails
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    try {
      // Extract and validate process variables using Bean Validation
      final ClaimRejectionDecisionRequest request =
          extractAndValidateDecisionVariables(externalTask);

      log.info(
          "Processing claim rejection based on adjuster decision - Claim ID: {}, Adjuster ID: {}",
          request.claimId(),
          request.adjusterId());

      // Format and send notification to customer with decision details
      final String notificationMessage =
          formatNotificationMessage(
              NOTIFICATION_MESSAGE, request.toBasicRequest(), request.decisionNotes());

      final boolean notificationSent =
          sendRejectionNotification(request.customerNotificationEmail(), notificationMessage);

      // Create and persist claim decision with adjuster information
      final ClaimType claimType = ClaimType.valueOf(request.claimType());
      final ClaimDecisionDto claimDecisionDto =
          createRejectionDecision(
              request.claimId(),
              request.adjusterId(),
              "ADJUSTER", // Decision maker name for adjuster decisions
              DECISION_MESSAGE,
              request.decisionNotes());

      final ClaimDecisionDto createdDecision =
          claimService.createClaimDecision(claimDecisionDto, claimType);

      if (createdDecision != null) {
        logSuccessfulProcessing(request.toBasicRequest(), createdDecision, notificationSent);
      } else {
        log.error("Failed to create claim decision for claim ID: {}", request.claimId());
        throw new IllegalStateException("Failed to create claim decision");
      }

    } catch (Exception ex) {
      log.error(
          "Error processing claim rejection based on adjuster decision: {}", ex.getMessage(), ex);
      throw new IllegalStateException("Failed to process claim rejection", ex);
    }
  }

  /**
   * Extracts process variables specific to adjuster decision rejections and validates them.
   *
   * @param externalTask the external task containing process variables
   * @return validated ClaimRejectionDecisionRequest containing all required data
   * @throws IllegalArgumentException if any validation constraint is violated
   */
  private ClaimRejectionDecisionRequest extractAndValidateDecisionVariables(
      ExternalTask externalTask) {
    final ClaimRejectionDecisionRequest request =
        new ClaimRejectionDecisionRequest(
            externalTask.getVariable(ProcInstVars.CLAIM_ID),
            externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER),
            externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME),
            externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME),
            externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL),
            externalTask.getVariable(ProcInstVars.POLICY_NUMBER),
            externalTask.getVariable(ProcInstVars.CLAIM_TYPE),
            externalTask.getVariable(ProcInstVars.DECISION_NOTES),
            externalTask.getVariable(ProcInstVars.ADJUSTER_ID));

    // Validate using Bean Validation
    validateObject(request);

    return request;
  }

  /**
   * Returns the Camunda topic name that this worker subscribes to.
   *
   * @return the topic name for adjuster decision-based claim rejections
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
