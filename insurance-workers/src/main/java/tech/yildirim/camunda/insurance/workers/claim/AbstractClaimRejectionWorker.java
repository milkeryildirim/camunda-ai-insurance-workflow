package tech.yildirim.camunda.insurance.workers.claim;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto.DecisionTypeEnum;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;

/**
 * Abstract base class for claim rejection workers providing common functionality.
 *
 * <p>This class provides shared functionality for different types of claim rejection workers,
 * including:
 *
 * <ul>
 *   <li>Variable extraction and validation using Bean Validation
 *   <li>Notification message formatting and sending
 *   <li>Claim decision creation and persistence
 *   <li>Error handling and logging
 * </ul>
 *
 * <p>Concrete implementations should extend this class and implement the specific rejection logic
 * in the {@link #executeBusinessLogic(ExternalTask, ExternalTaskService)} method.
 *
 * @author Yildirim
 * @since 1.0
 */
@Slf4j
public abstract class AbstractClaimRejectionWorker extends AbstractCamundaWorker {

  protected final ClaimService claimService;
  protected final NotificationService notificationService;
  protected final Validator validator;

  /**
   * Constructor for AbstractClaimRejectionWorker.
   *
   * @param claimService the service for claim operations
   * @param notificationService the service for sending notifications
   * @param validator the validator for Bean Validation
   */
  protected AbstractClaimRejectionWorker(
      ClaimService claimService, NotificationService notificationService, Validator validator) {
    this.claimService = claimService;
    this.notificationService = notificationService;
    this.validator = validator;
  }

  /**
   * Validates an object using Bean Validation and throws an exception if violations are found.
   *
   * @param object the object to validate
   * @param <T> the type of object being validated
   * @throws IllegalArgumentException if any validation constraint is violated
   */
  protected <T> void validateObject(T object) {
    final Set<ConstraintViolation<T>> violations = validator.validate(object);

    if (!violations.isEmpty()) {
      final StringBuilder errorMessage = new StringBuilder("Validation failed: ");
      violations.forEach(
          violation ->
              errorMessage
                  .append(violation.getPropertyPath())
                  .append(" ")
                  .append(violation.getMessage())
                  .append("; "));

      throw new IllegalArgumentException(errorMessage.toString());
    }
  }

  /**
   * Extracts process variables from the external task and validates them using Bean Validation.
   *
   * @param externalTask the external task containing process variables
   * @return validated ClaimRejectionRequest containing all required data
   * @throws IllegalArgumentException if any validation constraint is violated
   */
  protected ClaimRejectionRequest extractAndValidateVariables(ExternalTask externalTask) {
    final ClaimRejectionRequest request =
        new ClaimRejectionRequest(
            externalTask.getVariable(ProcInstVars.CLAIM_ID),
            externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER),
            externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME),
            externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME),
            externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL),
            externalTask.getVariable(ProcInstVars.POLICY_NUMBER),
            externalTask.getVariable(ProcInstVars.CLAIM_TYPE));

    // Validate using Bean Validation
    validateObject(request);

    return request;
  }

  /**
   * Sends a rejection notification email to the customer.
   *
   * @param notificationEmail email address to send the notification to
   * @param notificationMessage the formatted notification message to send
   * @return true if notification was sent successfully, false otherwise
   */
  protected boolean sendRejectionNotification(
      String notificationEmail, String notificationMessage) {
    log.debug("Sending rejection notification to customer: {}", notificationEmail);

    try {
      return notificationService.sendNotificationToCustomer(notificationEmail, notificationMessage);
    } catch (Exception ex) {
      log.error("Failed to send notification to {}: {}", notificationEmail, ex.getMessage(), ex);
      return false;
    }
  }

  /**
   * Creates a claim decision DTO representing a rejection.
   *
   * @param claimId the ID of the claim being rejected
   * @param decisionMakerId the ID of the decision maker (user or system)
   * @param decisionMakerName the name of the decision maker
   * @param reasoningMessage the reasoning for the rejection
   * @param rejectionReason the specific rejection reason
   * @return a fully populated ClaimDecisionDto with rejection details
   */
  protected ClaimDecisionDto createRejectionDecision(
      Long claimId,
      Long decisionMakerId,
      String decisionMakerName,
      String reasoningMessage,
      String rejectionReason) {
    return new ClaimDecisionDto()
        .claimId(claimId)
        .decisionDate(OffsetDateTime.now())
        .decisionType(DecisionTypeEnum.REJECTED)
        .decisionMakerId(decisionMakerId)
        .decisionMakerName(decisionMakerName)
        .approvedAmount(BigDecimal.ZERO)
        .reasoning(reasoningMessage)
        .rejectionReason(rejectionReason)
        .updatedAt(OffsetDateTime.now());
  }

  /**
   * Formats a notification message using the provided template and customer data.
   *
   * @param messageTemplate the message template with placeholders
   * @param request the claim rejection request containing customer data
   * @param additionalParams optional additional parameters for message formatting
   * @return the formatted notification message
   */
  protected String formatNotificationMessage(
      String messageTemplate, ClaimRejectionRequest request, Object... additionalParams) {
    Object[] baseParams = {
      request.customerFirstName(),
      request.customerLastName(),
      request.claimFileNumber(),
      request.policyNumber()
    };

    if (additionalParams.length > 0) {
      Object[] allParams = new Object[baseParams.length + additionalParams.length];
      System.arraycopy(baseParams, 0, allParams, 0, baseParams.length);
      System.arraycopy(additionalParams, 0, allParams, baseParams.length, additionalParams.length);
      return String.format(messageTemplate, allParams);
    }

    return String.format(messageTemplate, baseParams);
  }

  /**
   * Logs the successful processing of a claim rejection.
   *
   * @param request the claim rejection request
   * @param createdDecision the created claim decision
   * @param notificationSent whether the notification was sent successfully
   */
  protected void logSuccessfulProcessing(
      ClaimRejectionRequest request, ClaimDecisionDto createdDecision, boolean notificationSent) {
    log.info(
        "Successfully processed claim rejection - Claim ID: {}, Decision ID: {}, Notification sent: {}",
        request.claimId(),
        createdDecision.getId(),
        notificationSent);
  }
}
