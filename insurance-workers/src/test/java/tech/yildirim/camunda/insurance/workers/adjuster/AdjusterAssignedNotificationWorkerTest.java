package tech.yildirim.camunda.insurance.workers.adjuster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;

/**
 * Unit tests for {@link AdjusterAssignedNotificationWorker}.
 *
 * <p>This test class validates the external task worker responsible for sending adjuster assignment
 * notifications to customers. The worker extracts customer and claim information from process
 * variables, formats notification messages, and delegates sending to {@link NotificationService}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Topic name verification
 *   <li>Happy path scenarios with message formatting
 *   <li>Input validation and error handling
 *   <li>Service interaction and exception handling
 *   <li>Process variable setting and completion
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdjusterAssignedNotificationWorker Tests")
class AdjusterAssignedNotificationWorkerTest {

  @Mock private NotificationService notificationService;
  @Mock private ExternalTask externalTask;
  @Mock private ExternalTaskService externalTaskService;

  @Captor private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private AdjusterAssignedNotificationWorker worker;

  @BeforeEach
  void setUp() {
    worker = new AdjusterAssignedNotificationWorker(notificationService);
  }

  @Nested
  @DisplayName("Topic Name Tests")
  class TopicNameTests {

    @Test
    @DisplayName("Should return correct topic name")
    void shouldReturnCorrectTopicName() {
      // When
      String topicName = worker.getTopicName();

      // Then
      assertThat(topicName).isEqualTo("insurance.claim.notification.adjuster-assigned");
    }
  }

  @Nested
  @DisplayName("Happy Path Tests")
  class HappyPathTests {

    @Test
    @DisplayName("Should successfully send adjuster assignment notification")
    void shouldSuccessfullySendAdjusterAssignmentNotification() throws Exception {
      // Given
      String customerFirstName = "John";
      String customerLastName = "Doe";
      String claimFileNumber = "CLM-2023-001";
      String email = "john.doe@example.com";
      String adjusterName = "Jane Smith";

      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn(customerFirstName);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn(customerLastName);
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn(claimFileNumber);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL)).thenReturn(email);
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn(adjusterName);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(emailCaptor.capture(), messageCaptor.capture());

      assertThat(emailCaptor.getValue()).isEqualTo(email);
      String capturedMessage = messageCaptor.getValue();
      assertThat(capturedMessage)
          .contains(customerFirstName, customerLastName, claimFileNumber, adjusterName);

      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());
      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry(ProcInstVars.ADJUSTER_ASSIGNED_NOTIFICATION_SENT_SUCCESSFULLY, true)
          .containsKey(ProcInstVars.ADJUSTER_ASSIGNED_NOTIFICATION_MESSAGE);
    }

    @ParameterizedTest(name = "Should handle different customer names: {0} {1}")
    @CsvSource({"Anna, Schmidt", "José, García", "李, 伟", "O'Connor, Smith-Jones"})
    @DisplayName("Should handle various customer name formats")
    void shouldHandleVariousCustomerNameFormats(String firstName, String lastName)
        throws Exception {
      // Given
      String claimFileNumber = "CLM-2023-002";
      String email = "customer@example.com";
      String adjusterName = "Mike Johnson";

      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn(firstName);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn(lastName);
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn(claimFileNumber);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL)).thenReturn(email);
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn(adjusterName);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(notificationService)
          .sendNotificationToCustomer(
              eq(email),
              org.mockito.ArgumentMatchers.contains("Dear " + firstName + " " + lastName));
    }

    @Test
    @DisplayName("Should trim whitespace from process variables")
    void shouldTrimWhitespaceFromProcessVariables() throws Exception {
      // Given
      String customerFirstName = "  John  ";
      String customerLastName = "  Doe  ";
      String claimFileNumber = "  CLM-2023-003  ";
      String email = "  john.doe@example.com  ";
      String adjusterName = "  Jane Smith  ";

      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn(customerFirstName);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn(customerLastName);
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn(claimFileNumber);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL)).thenReturn(email);
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn(adjusterName);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(emailCaptor.capture(), messageCaptor.capture());

      assertThat(emailCaptor.getValue()).isEqualTo(email.trim());
      String capturedMessage = messageCaptor.getValue();
      assertThat(capturedMessage).contains("Dear John Doe", "CLM-2023-003", "Jane Smith");
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid customer first name")
    void shouldThrowExceptionForInvalidCustomerFirstName(String invalidFirstName) {
      // Given - Only stub the field being tested since validation fails early
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn(invalidFirstName);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer first name is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid customer last name")
    void shouldThrowExceptionForInvalidCustomerLastName(String invalidLastName) {
      // Given - Only stub fields up to the invalid one since validation fails early
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn(invalidLastName);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer last name is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid claim file number")
    void shouldThrowExceptionForInvalidClaimFileNumber(String invalidClaimFileNumber) {
      // Given - Only stub fields up to the invalid one since validation fails early
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER))
          .thenReturn(invalidClaimFileNumber);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Claim file number is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid customer email")
    void shouldThrowExceptionForInvalidCustomerEmail(String invalidEmail) {
      // Given - Only stub fields up to the invalid one since validation fails early
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-001");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
          .thenReturn(invalidEmail);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Customer notification email is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid adjuster name")
    void shouldThrowExceptionForInvalidAdjusterName(String invalidAdjusterName) {
      // Given
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-001");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
          .thenReturn("test@example.com");
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn(invalidAdjusterName);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Adjuster name is required");
    }
  }

  @Nested
  @DisplayName("Service Interaction Tests")
  class ServiceInteractionTests {

    @Test
    @DisplayName("Should handle notification service exception")
    void shouldHandleNotificationServiceException() {
      // Given
      setupValidProcessVariables();
      doThrow(new RuntimeException("Email service unavailable"))
          .when(notificationService)
          .sendNotificationToCustomer(
              org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Email service unavailable");
    }

    @Test
    @DisplayName("Should verify notification service is called with correct parameters")
    void shouldVerifyNotificationServiceIsCalledWithCorrectParameters() throws Exception {
      // Given
      String email = "customer@example.com";
      setupValidProcessVariables();
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL)).thenReturn(email);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(emailCaptor.capture(), messageCaptor.capture());

      assertThat(emailCaptor.getValue()).isEqualTo(email);
      assertThat(messageCaptor.getValue()).isNotNull().isNotBlank();
    }

    private void setupValidProcessVariables() {
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-001");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
          .thenReturn("test@example.com");
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn("Jane Smith");
    }
  }

  @Nested
  @DisplayName("Message Template Tests")
  class MessageTemplateTests {

    @Test
    @DisplayName("Should format message template correctly")
    void shouldFormatMessageTemplateCorrectly() throws Exception {
      // Given
      String customerFirstName = "Alice";
      String customerLastName = "Johnson";
      String claimFileNumber = "CLM-2023-999";
      String adjusterName = "Bob Wilson";

      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn(customerFirstName);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn(customerLastName);
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn(claimFileNumber);
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
          .thenReturn("alice@example.com");
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn(adjusterName);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(
              org.mockito.ArgumentMatchers.anyString(), messageCaptor.capture());

      String formattedMessage = messageCaptor.getValue();
      assertThat(formattedMessage)
          .startsWith("Dear Alice Johnson,")
          .contains("your claim (File #CLM-2023-999)")
          .contains("Name: Bob Wilson")
          .contains("Sincerely,\nThe Insurance Team");
    }

    @Test
    @DisplayName("Should store formatted message in process variables")
    void shouldStoreFormattedMessageInProcessVariables() throws Exception {
      // Given
      setupValidMessageTemplateVariables();

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());
      Map<String, Object> variables = variablesCaptor.getValue();

      String storedMessage =
          (String) variables.get(ProcInstVars.ADJUSTER_ASSIGNED_NOTIFICATION_MESSAGE);
      assertThat(storedMessage)
          .isNotNull()
          .contains("Dear Test Customer,")
          .contains("CLM-TEST-001")
          .contains("Test Adjuster");
    }

    private void setupValidMessageTemplateVariables() {
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("Test");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Customer");
      when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-TEST-001");
      when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
          .thenReturn("test@example.com");
      when(externalTask.getVariable(ProcInstVars.ADJUSTER_NAME)).thenReturn("Test Adjuster");
    }
  }
}
