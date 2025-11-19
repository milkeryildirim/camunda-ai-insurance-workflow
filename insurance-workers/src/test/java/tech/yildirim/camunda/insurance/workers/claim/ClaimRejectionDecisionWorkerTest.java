package tech.yildirim.camunda.insurance.workers.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;

/**
 * Comprehensive test suite for ClaimRejectionDecisionWorker.
 *
 * <p>This test class verifies the complete functionality of the ClaimRejectionDecisionWorker,
 * including:
 *
 * <ul>
 *   <li>Topic name configuration
 *   <li>Happy path execution scenarios
 *   <li>Input validation handling
 *   <li>Error handling and exception scenarios
 *   <li>Different claim types processing
 *   <li>Notification message formatting
 *   <li>Claim decision creation with adjuster details
 * </ul>
 *
 * @author Yildirim
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimRejectionDecisionWorker Tests")
class ClaimRejectionDecisionWorkerTest {

  @Mock private ClaimService claimService;
  @Mock private NotificationService notificationService;
  @Mock private Validator validator;
  @Mock private ExternalTask externalTask;
  @Mock private ExternalTaskService externalTaskService;

  @Captor private ArgumentCaptor<ClaimDecisionDto> claimDecisionCaptor;

  private ClaimRejectionDecisionWorker worker;

  @BeforeEach
  void setUp() {
    worker = new ClaimRejectionDecisionWorker(claimService, notificationService, validator);
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
      assertThat(topicName).isEqualTo("insurance.claim.reject-by-decision");
    }
  }

  @Nested
  @DisplayName("Happy Path Tests")
  class HappyPathTests {

    @Test
    @DisplayName("Should successfully process claim rejection with adjuster decision")
    void shouldSuccessfullyProcessClaimRejectionWithAdjusterDecision() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(notificationService).sendNotificationToCustomer(eq("john.doe@example.com"), any());
      verify(claimService).createClaimDecision(any(), eq(ClaimType.AUTO));
    }

    @Test
    @DisplayName("Should handle notification failure gracefully")
    void shouldHandleNotificationFailureGracefully() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(false);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When & Then - Should not throw exception even if notification fails
      worker.executeBusinessLogic(externalTask, externalTaskService);

      verify(claimService).createClaimDecision(any(), eq(ClaimType.AUTO));
    }

    @Test
    @DisplayName("Should handle notification service exception gracefully")
    void shouldHandleNotificationServiceExceptionGracefully() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any()))
          .thenThrow(new RuntimeException("Email service down"));
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When & Then - Should not throw exception even if notification service fails
      worker.executeBusinessLogic(externalTask, externalTaskService);

      verify(claimService).createClaimDecision(any(), eq(ClaimType.AUTO));
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should throw exception when validation fails")
    void shouldThrowExceptionWhenValidationFails() {
      // Given
      setupValidExternalTask();
      ConstraintViolation<ClaimRejectionDecisionRequest> violation = createMockViolation();
      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Set.of(violation));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to process claim rejection")
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .satisfies(
              throwable -> {
                String rootCauseMessage = throwable.getCause().getMessage();
                assertThat(rootCauseMessage)
                    .startsWith("Validation failed:")
                    .contains("Validation error");
              });
    }

    @Test
    @DisplayName("Should validate claim ID is positive")
    void shouldValidateClaimIdIsPositive() {
      // Given
      ClaimRejectionDecisionRequest request =
          new ClaimRejectionDecisionRequest(
              -1L, // Invalid claim ID (negative)
              "FILE-123",
              "John",
              "Doe",
              "john@example.com",
              "POL-123",
              "AUTO",
              "Insufficient documentation provided",
              1001L);

      // When
      Set<ConstraintViolation<ClaimRejectionDecisionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("must be positive");
    }

    @Test
    @DisplayName("Should validate adjuster ID is positive")
    void shouldValidateAdjusterIdIsPositive() {
      // Given
      ClaimRejectionDecisionRequest request =
          new ClaimRejectionDecisionRequest(
              12345L,
              "FILE-123",
              "John",
              "Doe",
              "john@example.com",
              "POL-123",
              "AUTO",
              "Insufficient documentation provided",
              -1L); // Invalid adjuster ID (negative)

      // When
      Set<ConstraintViolation<ClaimRejectionDecisionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("must be positive");
    }

    @Test
    @DisplayName("Should validate email format")
    void shouldValidateEmailFormat() {
      // Given
      ClaimRejectionDecisionRequest request =
          new ClaimRejectionDecisionRequest(
              12345L,
              "FILE-123",
              "John",
              "Doe",
              "invalid-email", // Invalid email format
              "POL-123",
              "AUTO",
              "Insufficient documentation provided",
              1001L);

      // When
      Set<ConstraintViolation<ClaimRejectionDecisionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("valid email address");
    }

    @Test
    @DisplayName("Should validate decision notes are not blank")
    void shouldValidateDecisionNotesAreNotBlank() {
      // Given
      ClaimRejectionDecisionRequest request =
          new ClaimRejectionDecisionRequest(
              12345L,
              "FILE-123",
              "John",
              "Doe",
              "john@example.com",
              "POL-123",
              "AUTO",
              "", // Empty decision notes
              1001L);

      // When
      Set<ConstraintViolation<ClaimRejectionDecisionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("cannot be blank");
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception when claim decision creation fails")
    void shouldThrowExceptionWhenClaimDecisionCreationFails() {
      // Given
      setupValidExternalTask();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to process claim rejection")
          .hasCauseInstanceOf(IllegalStateException.class)
          .satisfies(
              throwable -> {
                String causeMessage = throwable.getCause().getMessage();
                assertThat(causeMessage).contains("Failed to create claim decision");
              });
    }

    @Test
    @DisplayName("Should handle claim service exception")
    void shouldHandleClaimServiceException() {
      // Given
      setupValidExternalTask();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO)))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to process claim rejection")
          .hasCauseInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Different Claim Types Tests")
  class DifferentClaimTypesTests {

    @Test
    @DisplayName("Should process HEALTH claim type correctly")
    void shouldProcessHealthClaimTypeCorrectly() {
      // Given
      setupValidExternalTaskWithClaimType("HEALTH");
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.HEALTH))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).createClaimDecision(any(), eq(ClaimType.HEALTH));
    }

    @Test
    @DisplayName("Should process HOME claim type correctly")
    void shouldProcessHomeClaimTypeCorrectly() {
      // Given
      setupValidExternalTaskWithClaimType("HOME");
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.HOME))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).createClaimDecision(any(), eq(ClaimType.HOME));
    }
  }

  @Nested
  @DisplayName("Claim Decision Creation Tests")
  class ClaimDecisionCreationTests {

    @Test
    @DisplayName("Should create proper claim decision with adjuster details")
    void shouldCreateProperClaimDecisionWithAdjusterDetails() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).createClaimDecision(claimDecisionCaptor.capture(), eq(ClaimType.AUTO));

      ClaimDecisionDto capturedDecision = claimDecisionCaptor.getValue();
      assertThat(capturedDecision.getClaimId()).isEqualTo(12345L);
      assertThat(capturedDecision.getDecisionType())
          .isEqualTo(ClaimDecisionDto.DecisionTypeEnum.REJECTED);
      assertThat(capturedDecision.getApprovedAmount()).isEqualTo(BigDecimal.ZERO);
      assertThat(capturedDecision.getReasoning())
          .isEqualTo("Claim rejected based on adjuster's assessment.");
      assertThat(capturedDecision.getRejectionReason())
          .isEqualTo("Insufficient documentation provided for claim approval");
      assertThat(capturedDecision.getDecisionMakerId()).isEqualTo(1001L);
      assertThat(capturedDecision.getDecisionMakerName()).isEqualTo("ADJUSTER");
      assertThat(capturedDecision.getDecisionDate()).isNotNull();
      assertThat(capturedDecision.getUpdatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Notification Message Tests")
  class NotificationMessageTests {

    @Test
    @DisplayName("Should format notification message correctly with adjuster notes")
    void shouldFormatNotificationMessageCorrectlyWithAdjusterNotes() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(eq("john.doe@example.com"), messageCaptor.capture());

      String sentMessage = messageCaptor.getValue();
      assertThat(sentMessage)
          .contains("Dear John Doe")
          .contains("CLM-2024-001")
          .contains("POL-12345")
          .contains("ADJUSTER'S ASSESSMENT NOTES:")
          .contains("Insufficient documentation provided for claim approval")
          .contains("Claims Department at +1-800-555-0199")
          .contains("unable to approve your claim request");
    }

    @Test
    @DisplayName("Should include proper appeal information in notification")
    void shouldIncludeProperAppealInformationInNotification() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(notificationService)
          .sendNotificationToCustomer(eq("john.doe@example.com"), messageCaptor.capture());

      String sentMessage = messageCaptor.getValue();
      assertThat(sentMessage)
          .contains("right to appeal this decision")
          .contains("additional information that was not previously submitted")
          .contains("referencing your claim file number");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should handle complete workflow from start to finish")
    void shouldHandleCompleteWorkflowFromStartToFinish() {
      // Given
      setupValidExternalTask();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionDecisionRequest.class)))
          .thenReturn(Collections.emptySet());
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then - Verify the complete workflow execution
      verify(validator).validate(any(ClaimRejectionDecisionRequest.class));
      verify(notificationService).sendNotificationToCustomer(any(), any());
      verify(claimService).createClaimDecision(any(), any());

      // Verify the captured decision has all the right properties
      verify(claimService).createClaimDecision(claimDecisionCaptor.capture(), any());
      ClaimDecisionDto decision = claimDecisionCaptor.getValue();
      assertThat(decision.getClaimId()).isEqualTo(12345L);
      assertThat(decision.getDecisionMakerId()).isEqualTo(1001L);
      assertThat(decision.getDecisionMakerName()).isEqualTo("ADJUSTER");
    }
  }

  // Helper methods for creating test data
  private void setupValidExternalTask() {
    setupValidExternalTaskWithClaimType("AUTO");
  }

  private void setupValidExternalTaskWithClaimType(String claimType) {
    when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(12345L);
    when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-2024-001");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
        .thenReturn("john.doe@example.com");
    when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn("POL-12345");
    when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
    when(externalTask.getVariable(ProcInstVars.DECISION_NOTES))
        .thenReturn("Insufficient documentation provided for claim approval");
    when(externalTask.getVariable(ProcInstVars.ADJUSTER_ID)).thenReturn(1001L);
  }

  private ClaimDecisionDto createMockClaimDecision() {
    ClaimDecisionDto decision = new ClaimDecisionDto();
    decision.setId(100L);
    decision.setClaimId(12345L);
    decision.setDecisionType(ClaimDecisionDto.DecisionTypeEnum.REJECTED);
    decision.setDecisionDate(OffsetDateTime.now());
    decision.setDecisionMakerId(1001L);
    decision.setDecisionMakerName("ADJUSTER");
    return decision;
  }

  @SuppressWarnings("unchecked")
  private ConstraintViolation<ClaimRejectionDecisionRequest> createMockViolation() {
    ConstraintViolation<ClaimRejectionDecisionRequest> violation =
        org.mockito.Mockito.mock(ConstraintViolation.class);
    when(violation.getPropertyPath())
        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
    when(violation.getMessage()).thenReturn("Validation error");
    return violation;
  }

  private jakarta.validation.Validator createRealValidator() {
    try (jakarta.validation.ValidatorFactory factory =
        jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator();
    }
  }
}
