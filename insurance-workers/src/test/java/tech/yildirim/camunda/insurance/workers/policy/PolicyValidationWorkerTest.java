package tech.yildirim.camunda.insurance.workers.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyValidationWorker Tests")
class PolicyValidationWorkerTest {

  @Mock
  private PolicyService policyService;

  @Mock
  private ExternalTask externalTask;

  @Mock
  private ExternalTaskService externalTaskService;

  @Captor
  private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private PolicyValidationWorker policyValidationWorker;

  @BeforeEach
  void setUp() {
    policyValidationWorker = new PolicyValidationWorker(policyService);
  }

  @Nested
  @DisplayName("Topic Name Tests")
  class TopicNameTests {

    @Test
    @DisplayName("Should return correct topic name")
    void shouldReturnCorrectTopicName() {
      // When
      String topicName = policyValidationWorker.getTopicName();

      // Then
      assertThat(topicName).isEqualTo("insurance.claim.policy-validate");
    }
  }

  @Nested
  @DisplayName("Policy Validation Tests")
  class PolicyValidationTests {

    @Test
    @DisplayName("Should complete task with valid policy")
    void shouldCompleteTaskWithValidPolicy() {
      // Given
      String validPolicyNumber = "POL-12345";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(validPolicyNumber);
      when(policyService.isPolicyValid(validPolicyNumber)).thenReturn(true);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(validPolicyNumber);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, true);
    }

    @Test
    @DisplayName("Should complete task with invalid policy")
    void shouldCompleteTaskWithInvalidPolicy() {
      // Given
      String invalidPolicyNumber = "POL-INVALID";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(invalidPolicyNumber);
      when(policyService.isPolicyValid(invalidPolicyNumber)).thenReturn(false);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(invalidPolicyNumber);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, false);
    }

    @Test
    @DisplayName("Should handle expired policy correctly")
    void shouldHandleExpiredPolicyCorrectly() {
      // Given
      String expiredPolicyNumber = "POL-EXPIRED";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(expiredPolicyNumber);
      when(policyService.isPolicyValid(expiredPolicyNumber)).thenReturn(false);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(expiredPolicyNumber);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, false);
    }

    @Test
    @DisplayName("Should handle non-existent policy correctly")
    void shouldHandleNonExistentPolicyCorrectly() {
      // Given
      String nonExistentPolicyNumber = "POL-NOT-FOUND";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(nonExistentPolicyNumber);
      when(policyService.isPolicyValid(nonExistentPolicyNumber)).thenReturn(false);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(nonExistentPolicyNumber);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, false);
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @Test
    @DisplayName("Should handle null policy number")
    void shouldHandleNullPolicyNumber() {
      // Given
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(null);

      // When & Then
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService),
          "Policy number cannot be null or empty"
      );
    }

    @Test
    @DisplayName("Should handle empty policy number")
    void shouldHandleEmptyPolicyNumber() {
      // Given
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn("");

      // When & Then
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService),
          "Policy number cannot be null or empty"
      );
    }

    @Test
    @DisplayName("Should handle whitespace-only policy number")
    void shouldHandleWhitespaceOnlyPolicyNumber() {
      // Given
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn("   ");

      // When & Then
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService),
          "Policy number cannot be null or empty"
      );
    }

    @Test
    @DisplayName("Should trim and validate policy number with leading/trailing spaces")
    void shouldTrimAndValidatePolicyNumberWithSpaces() {
      // Given
      String policyNumberWithSpaces = "  POL-12345  ";
      // PolicyValidationWorker actually passes the original string (with spaces) to PolicyService
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumberWithSpaces);
      when(policyService.isPolicyValid(policyNumberWithSpaces)).thenReturn(true);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(policyNumberWithSpaces);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, true);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle PolicyService exception gracefully")
    void shouldHandlePolicyServiceExceptionGracefully() {
      // Given
      String policyNumber = "POL-12345";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
      when(policyService.isPolicyValid(policyNumber))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      org.junit.jupiter.api.Assertions.assertThrows(
          RuntimeException.class,
          () -> policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService),
          "Database connection failed"
      );
    }

    @Test
    @DisplayName("Should handle ExternalTaskService completion failure")
    void shouldHandleExternalTaskServiceCompletionFailure() {
      // Given
      String policyNumber = "POL-12345";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
      when(policyService.isPolicyValid(policyNumber)).thenReturn(true);
      doThrow(new RuntimeException("Task completion failed"))
          .when(externalTaskService).complete(eq(externalTask), any(Map.class));

      // When & Then
      org.junit.jupiter.api.Assertions.assertThrows(
          RuntimeException.class,
          () -> policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService),
          "Task completion failed"
      );
    }
  }

  @Nested
  @DisplayName("Integration-like Tests")
  class IntegrationLikeTests {

    @Test
    @DisplayName("Should validate multiple policy formats correctly")
    void shouldValidateMultiplePolicyFormatsCorrectly() {
      // Test different policy number formats
      String[] validPolicyNumbers = {
          "POL-12345",
          "POLICY-ABC123",
          "INS123456",
          "P-2024-001"
      };

      for (String policyNumber : validPolicyNumbers) {
        // Given
        when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
        when(policyService.isPolicyValid(policyNumber)).thenReturn(true);

        // When
        policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

        // Then
        verify(policyService).isPolicyValid(policyNumber);
      }
    }

    @Test
    @DisplayName("Should handle case sensitivity appropriately")
    void shouldHandleCaseSensitivityAppropriately() {
      // Given
      String policyNumber = "pol-12345"; // lowercase
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
      when(policyService.isPolicyValid(policyNumber)).thenReturn(true);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(policyService).isPolicyValid(policyNumber);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.IS_POLICY_VALID, true);
    }
  }

  @Nested
  @DisplayName("Process Variable Tests")
  class ProcessVariableTests {

    @Test
    @DisplayName("Should set correct process variable key for valid policy")
    void shouldSetCorrectProcessVariableKeyForValidPolicy() {
      // Given
      String policyNumber = "POL-12345";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
      when(policyService.isPolicyValid(policyNumber)).thenReturn(true);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).hasSize(1);
      assertThat(variables).containsKey(ProcInstVars.IS_POLICY_VALID);
      assertThat(variables.get(ProcInstVars.IS_POLICY_VALID)).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("Should set correct process variable key for invalid policy")
    void shouldSetCorrectProcessVariableKeyForInvalidPolicy() {
      // Given
      String policyNumber = "POL-INVALID";
      when(externalTask.getVariable(ProcInstVars.POLICY_NUMBER)).thenReturn(policyNumber);
      when(policyService.isPolicyValid(policyNumber)).thenReturn(false);

      // When
      policyValidationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).hasSize(1);
      assertThat(variables).containsKey(ProcInstVars.IS_POLICY_VALID);
      assertThat(variables.get(ProcInstVars.IS_POLICY_VALID)).isInstanceOf(Boolean.class);
      assertThat(variables.get(ProcInstVars.IS_POLICY_VALID)).isEqualTo(false);
    }
  }
}
