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
import java.util.Map;
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
import tech.yildirim.aiinsurance.api.generated.model.PolicyDto;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.notification.NotificationService;
import tech.yildirim.camunda.insurance.workers.policy.PolicyService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimRejectionInvalidPolicyWorker Tests")
class ClaimRejectionInvalidPolicyWorkerTest {

  @Mock private ClaimService claimService;
  @Mock private NotificationService notificationService;
  @Mock private PolicyService policyService;
  @Mock private Validator validator;
  @Mock private ExternalTask externalTask;
  @Mock private ExternalTaskService externalTaskService;

  @Captor private ArgumentCaptor<Map<String, Object>> processVariablesCaptor;
  @Captor private ArgumentCaptor<ClaimDecisionDto> claimDecisionCaptor;

  private ClaimRejectionInvalidPolicyWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new ClaimRejectionInvalidPolicyWorker(
            claimService, notificationService, policyService, validator);
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
      assertThat(topicName).isEqualTo("insurance.claim.reject-invalid-policy");
    }
  }

  @Nested
  @DisplayName("Happy Path Tests")
  class HappyPathTests {

    @Test
    @DisplayName("Should successfully process claim rejection with notification success")
    void shouldSuccessfullyProcessClaimRejectionWithNotificationSuccess() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).getPolicyByPolicyNumber("POL-12345");
      verify(notificationService).sendNotificationToCustomer(eq("john.doe@example.com"), any());
      verify(claimService).createClaimDecision(any(), eq(ClaimType.AUTO));
      verify(externalTaskService).complete(eq(externalTask), processVariablesCaptor.capture());

      Map<String, Object> processVariables = processVariablesCaptor.getValue();
      assertThat(processVariables)
          .containsEntry(
              ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY, true)
          .containsKey(ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_MESSAGE);
    }

    @Test
    @DisplayName("Should successfully process claim rejection with notification failure")
    void shouldSuccessfullyProcessClaimRejectionWithNotificationFailure() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(false);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), processVariablesCaptor.capture());

      Map<String, Object> processVariables = processVariablesCaptor.getValue();
      assertThat(processVariables)
          .containsEntry(
              ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY, false);
    }

    @Test
    @DisplayName("Should handle notification service exception gracefully")
    void shouldHandleNotificationServiceExceptionGracefully() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
      when(notificationService.sendNotificationToCustomer(any(), any()))
          .thenThrow(new RuntimeException("Email service down"));
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(mockDecision);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), processVariablesCaptor.capture());

      Map<String, Object> processVariables = processVariablesCaptor.getValue();
      assertThat(processVariables)
          .containsEntry(
              ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY, false);
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
      ConstraintViolation<ClaimRejectionRequest> violation = createMockViolation();
      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Set.of(violation));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
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
      // Given - This test doesn't use worker.executeBusinessLogic, so no need for external task
      // setup
      ClaimRejectionRequest request =
          new ClaimRejectionRequest(
              -1L, "FILE-123", "John", "Doe", "john@example.com", "POL-123", "AUTO");

      // When
      Set<ConstraintViolation<ClaimRejectionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("must be positive");
    }

    @Test
    @DisplayName("Should validate email format")
    void shouldValidateEmailFormat() {
      // Given
      ClaimRejectionRequest request =
          new ClaimRejectionRequest(
              1L, "FILE-123", "John", "Doe", "invalid-email", "POL-123", "AUTO");

      // When
      Set<ConstraintViolation<ClaimRejectionRequest>> violations =
          createRealValidator().validate(request);

      // Then
      assertThat(violations).isNotEmpty();
      assertThat(violations.iterator().next().getMessage()).contains("valid email address");
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception when policy not found")
    void shouldThrowExceptionWhenPolicyNotFound() {
      // Given
      setupValidExternalTask();
      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("Policy not found: POL-12345");
    }

    @Test
    @DisplayName("Should throw exception when claim decision creation fails")
    void shouldThrowExceptionWhenClaimDecisionCreationFails() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
      when(notificationService.sendNotificationToCustomer(any(), any())).thenReturn(true);
      when(claimService.createClaimDecision(any(), eq(ClaimType.AUTO))).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to process claim rejection")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle policy service exception")
    void shouldHandlePolicyServiceException() {
      // Given
      setupValidExternalTask();
      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345"))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
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
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
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
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
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
    @DisplayName("Should create proper claim decision with rejection details")
    void shouldCreateProperClaimDecisionWithRejectionDetails() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
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
      assertThat(capturedDecision.getReasoning()).contains("rejected due to invalid policy");
      assertThat(capturedDecision.getDecisionMakerId()).isEqualTo(-1L);
      assertThat(capturedDecision.getDecisionMakerName()).isEqualTo("SYSTEM");
    }
  }

  @Nested
  @DisplayName("Notification Message Tests")
  class NotificationMessageTests {

    @Test
    @DisplayName("Should format notification message correctly")
    void shouldFormatNotificationMessageCorrectly() {
      // Given
      setupValidExternalTask();
      PolicyDto mockPolicy = createMockPolicy();
      ClaimDecisionDto mockDecision = createMockClaimDecision();

      when(validator.validate(any(ClaimRejectionRequest.class))).thenReturn(Collections.emptySet());
      when(policyService.getPolicyByPolicyNumber("POL-12345")).thenReturn(mockPolicy);
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
          .contains("rejected due to an invalid policy");

      // Verify message is stored in process variables
      verify(externalTaskService).complete(eq(externalTask), processVariablesCaptor.capture());
      Map<String, Object> processVariables = processVariablesCaptor.getValue();
      assertThat(
              processVariables.get(ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_MESSAGE))
          .isEqualTo(sentMessage);
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
  }

  private void setupOtherValidVariables() {
    when(externalTask.getVariable(ProcInstVars.CLAIM_FILE_NUMBER)).thenReturn("CLM-2024-001");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_FIRSTNAME)).thenReturn("John");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_LASTNAME)).thenReturn("Doe");
    when(externalTask.getVariable(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL))
        .thenReturn("john.doe@example.com");
    when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn("POL-12345");
    when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
  }

  private PolicyDto createMockPolicy() {
    PolicyDto policy = new PolicyDto();
    policy.setPolicyNumber("POL-12345");
    policy.setCustomerId(1L);
    return policy;
  }

  private ClaimDecisionDto createMockClaimDecision() {
    ClaimDecisionDto decision = new ClaimDecisionDto();
    decision.setId(100L);
    decision.setClaimId(12345L);
    decision.setDecisionType(ClaimDecisionDto.DecisionTypeEnum.REJECTED);
    decision.setDecisionDate(OffsetDateTime.now());
    return decision;
  }

  @SuppressWarnings("unchecked")
  private ConstraintViolation<ClaimRejectionRequest> createMockViolation() {
    ConstraintViolation<ClaimRejectionRequest> violation =
        org.mockito.Mockito.mock(ConstraintViolation.class);
    when(violation.getPropertyPath())
        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
    when(violation.getMessage()).thenReturn("Validation error");
    return violation;
  }

  private jakarta.validation.Validator createRealValidator() {
    jakarta.validation.ValidatorFactory factory =
        jakarta.validation.Validation.buildDefaultValidatorFactory();
    return factory.getValidator();
  }
}
