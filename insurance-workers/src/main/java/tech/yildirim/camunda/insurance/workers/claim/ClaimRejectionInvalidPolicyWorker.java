package tech.yildirim.camunda.insurance.workers.claim;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.aiinsurance.api.generated.model.PolicyDto;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;
import tech.yildirim.camunda.insurance.workers.policy.PolicyService;

/**
 * Camunda external task worker responsible for processing claim rejections due to invalid policies.
 *
 * <p>This worker handles the business logic for rejecting insurance claims when the associated
 * policy is found to be invalid or non-existent. It performs the following operations:
 *
 * <ul>
 *   <li>Validates the policy associated with the claim
 *   <li>Creates a rejection decision record in the system
 *   <li>Sends notification to the customer about the claim rejection
 *   <li>Records the rejection reason and decision details
 * </ul>
 *
 * <p>The worker is triggered by the Camunda process engine when a claim needs to be rejected due to
 * policy validation failures. It integrates with multiple services to complete the rejection
 * workflow comprehensively.
 *
 * <p>This worker extends {@link AbstractClaimRejectionWorker} to leverage common rejection
 * functionality while providing specific behavior for invalid policy scenarios.
 *
 * @author Yildirim
 * @since 1.0
 */
@Component
@Slf4j
public class ClaimRejectionInvalidPolicyWorker extends AbstractClaimRejectionWorker {

  /** The Camunda topic name that this worker subscribes to. */
  private static final String TOPIC_NAME = "insurance.claim.reject-invalid-policy";

  /**
   * Template message sent to customers when their claim is rejected due to invalid policy.
   * Placeholders: customer first name, last name, claim file number, policy number.
   */
  private static final String NOTIFICATION_MESSAGE =
      """
       Dear %s %s,

       Unfortunately, we have to inform you that your recent claim with the reference number %s has been rejected due to an invalid policy.
       After careful review, we found that the policy number %s provided in your claim does not correspond to a valid policy in our system.

       If you have any questions, please contact our customer support.

       Best regards,
       The Insurance Team
      """;

  /** Standard decision message recorded in the system for invalid policy rejections. */
  private static final String DECISION_MESSAGE = "Claim has been rejected due to invalid policy";

  private final PolicyService policyService;

  /**
   * Constructor for ClaimRejectionInvalidPolicyWorker.
   *
   * @param claimService the service for claim operations
   * @param notificationService the service for sending notifications
   * @param validator the validator for Bean Validation
   * @param policyService the service for policy operations
   */
  public ClaimRejectionInvalidPolicyWorker(
      ClaimService claimService,
      NotificationService notificationService,
      Validator validator,
      PolicyService policyService) {
    super(claimService, notificationService, validator);
    this.policyService = policyService;
  }

  /**
   * Executes the business logic for rejecting a claim due to invalid policy.
   *
   * <p>This method orchestrates the complete claim rejection workflow:
   *
   * <ol>
   *   <li>Extracts claim and customer information from process variables
   *   <li>Validates input data using Bean Validation
   *   <li>Retrieves policy and customer data from external services
   *   <li>Sends rejection notification to the customer
   *   <li>Creates and persists the claim decision record
   *   <li>Records notification status and message as process variables
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
      final ClaimRejectionRequest request = extractAndValidateVariables(externalTask);

      log.info(
          "Processing claim rejection for invalid policy - Claim ID: {}, Policy: {}",
          request.claimId(),
          request.policyNumber());

      // Get policy and customer information
      final PolicyDto policy = policyService.getPolicyByPolicyNumber(request.policyNumber());
      if (policy == null) {
        log.warn("Policy not found for number: {}", request.policyNumber());
        throw new IllegalStateException("Policy not found: " + request.policyNumber());
      }

      // Send notification to customer and capture result
      final String notificationMessage = formatNotificationMessage(NOTIFICATION_MESSAGE, request);

      final boolean notificationSent =
          sendRejectionNotification(request.customerNotificationEmail(), notificationMessage);

      // Create and persist claim decision
      final ClaimType claimType = ClaimType.valueOf(request.claimType());
      final ClaimDecisionDto claimDecisionDto =
          createRejectionDecision(
              request.claimId(),
              -1L, // System decision
              "SYSTEM",
              DECISION_MESSAGE,
              DECISION_MESSAGE);

      final ClaimDecisionDto createdDecision =
          claimService.createClaimDecision(claimDecisionDto, claimType);

      if (createdDecision != null) {
        logSuccessfulProcessing(request, createdDecision, notificationSent);
      } else {
        log.error("Failed to create claim decision for claim ID: {}", request.claimId());
        throw new IllegalStateException("Failed to create claim decision");
      }

      // Complete task with notification status and message as process variables
      completeTask(
          externalTask,
          externalTaskService,
          java.util.Map.of(
              ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY,
                  notificationSent,
              ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_MESSAGE,
                  notificationMessage));

    } catch (Exception ex) {
      log.error("Error processing claim rejection for invalid policy: {}", ex.getMessage(), ex);
      throw new IllegalStateException("Failed to process claim rejection", ex);
    }
  }

  /**
   * Returns the Camunda topic name that this worker subscribes to.
   *
   * @return the topic name for invalid policy claim rejections
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
