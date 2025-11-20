package tech.yildirim.camunda.insurance.workers.adjuster;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;

/**
 * External task worker responsible for sending adjuster assignment notifications to customers.
 *
 * <p>This worker subscribes to the {@link #TOPIC_NAME} external task topic and handles the
 * notification process when an adjuster is assigned to a customer's insurance claim. The worker
 * extracts customer and claim information from process variables, formats a notification message
 * using a predefined template, and delegates the actual sending to {@link NotificationService}.
 *
 * <p>The notification includes:
 *
 * <ul>
 *   <li>Customer personal information (first name, last name)
 *   <li>Claim file number for reference
 *   <li>Assigned adjuster name
 *   <li>Instructions for next steps
 * </ul>
 *
 * <p>Upon successful completion, the worker sets process variables to indicate the notification
 * status and stores the sent message content for audit purposes.
 *
 * @see NotificationService
 * @see ProcInstVars
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdjusterAssignedNotificationWorker extends AbstractCamundaWorker {

  /** External task topic this worker subscribes to. */
  private static final String TOPIC_NAME = "insurance.claim.notification.adjuster-assigned";

  /**
   * Template message sent to customers when an adjuster is assigned to their claim.
   *
   * <p>The message template contains placeholders for dynamic content:
   *
   * <ol>
   *   <li>Customer first name
   *   <li>Customer last name
   *   <li>Claim file number
   *   <li>Assigned adjuster name
   * </ol>
   *
   * <p>The formatted message provides comprehensive information about the adjuster assignment and
   * sets expectations for the customer regarding next steps in the claims process.
   */
  public static final String NOTIFICATION_MESSAGE_TEMPLATE =
      """
      Dear %s %s,

      We are writing to provide an important update regarding your claim (File #%s).

      We have successfully assigned an independent adjuster to your case to assess the reported damages and verify the details of the incident.

      -------------------------------------------------------------
      ASSIGNED ADJUSTER INFORMATION:
      Name: %s
      -------------------------------------------------------------

      The adjuster will be contacting you shortly to schedule an inspection appointment or to request any additional documentation needed to complete the assessment report.

      Please ensure you remain accessible via your registered contact phone number to avoid delays in the process.

      Sincerely,
      The Insurance Team
      """;

  /** Service that handles the actual notification delivery. */
  private final NotificationService notificationService;

  /**
   * Executes the business logic for sending adjuster assignment notifications to customers.
   *
   * <p>This method performs the following operations:
   *
   * <ol>
   *   <li>Extracts and validates required customer and claim information from process variables
   *   <li>Formats the notification message using the predefined template
   *   <li>Delegates the actual notification sending to {@link NotificationService}
   *   <li>Completes the external task with notification status and message content
   * </ol>
   *
   * <p>Required process variables:
   *
   * <ul>
   *   <li>{@link ProcInstVars#CUSTOMER_FIRSTNAME} - Customer's first name
   *   <li>{@link ProcInstVars#CUSTOMER_LASTNAME} - Customer's last name
   *   <li>{@link ProcInstVars#CLAIM_FILE_NUMBER} - Claim file reference number
   *   <li>{@link ProcInstVars#CUSTOMER_NOTIFICATION_EMAIL} - Customer's email address
   *   <li>{@link ProcInstVars#ADJUSTER_NAME} - Name of the assigned adjuster
   * </ul>
   *
   * <p>Upon completion, sets the following process variables:
   *
   * <ul>
   *   <li>{@link ProcInstVars#ADJUSTER_ASSIGNED_NOTIFICATION_SENT_SUCCESSFULLY} - boolean
   *       indicating success
   *   <li>{@link ProcInstVars#ADJUSTER_ASSIGNED_NOTIFICATION_MESSAGE} - the formatted message
   *       content
   * </ul>
   *
   * @param externalTask the external task providing input variables
   * @param externalTaskService the external task service used to complete the task
   * @throws IllegalArgumentException if required process variables are missing or invalid
   * @throws Exception if notification sending fails
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) throws Exception {

    // Extract and validate required process variables
    final String customerFirstName =
        extractAndValidateVariable(
            externalTask, ProcInstVars.CUSTOMER_FIRSTNAME, "Customer first name");
    final String customerLastName =
        extractAndValidateVariable(
            externalTask, ProcInstVars.CUSTOMER_LASTNAME, "Customer last name");
    final String claimFileNumber =
        extractAndValidateVariable(
            externalTask, ProcInstVars.CLAIM_FILE_NUMBER, "Claim file number");
    final String email =
        extractAndValidateVariable(
            externalTask, ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL, "Customer notification email");
    final String adjusterName =
        extractAndValidateVariable(externalTask, ProcInstVars.ADJUSTER_NAME, "Adjuster name");

    // Format the notification message using the template
    final String formattedMessage =
        String.format(
            NOTIFICATION_MESSAGE_TEMPLATE,
            customerFirstName,
            customerLastName,
            claimFileNumber,
            adjusterName);

    log.debug(
        "Sending adjuster assignment notification to customer {} {} for claim {}",
        customerFirstName,
        customerLastName,
        claimFileNumber);

    // Delegate to notification service
    notificationService.sendNotificationToCustomer(email, formattedMessage);

    // Complete task with notification status and message content
    completeTask(
        externalTask,
        externalTaskService,
        Map.of(
            ProcInstVars.ADJUSTER_ASSIGNED_NOTIFICATION_SENT_SUCCESSFULLY,
            true,
            ProcInstVars.ADJUSTER_ASSIGNED_NOTIFICATION_MESSAGE,
            formattedMessage));

    log.info(
        "Successfully sent adjuster assignment notification to {} for claim {}",
        email,
        claimFileNumber);
  }

  /**
   * Extracts and validates a required process variable from the external task.
   *
   * @param externalTask the external task containing process variables
   * @param variableName the name of the process variable to extract
   * @param displayName the human-readable name for error messages
   * @return the extracted and validated variable value
   * @throws IllegalArgumentException if the variable is null or empty
   */
  private String extractAndValidateVariable(
      ExternalTask externalTask, String variableName, String displayName) {
    final String value = externalTask.getVariable(variableName);

    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(
          String.format("%s is required (process variable: '%s')", displayName, variableName));
    }

    return value.trim();
  }

  /**
   * Returns the external task topic name this worker subscribes to.
   *
   * @return the topic name for adjuster assignment notifications
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
