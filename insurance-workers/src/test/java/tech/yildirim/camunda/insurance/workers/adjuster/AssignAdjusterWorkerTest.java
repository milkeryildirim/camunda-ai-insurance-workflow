package tech.yildirim.camunda.insurance.workers.adjuster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * Unit tests for {@link AssignAdjusterWorker}.
 *
 * <p>This test class validates the external task worker responsible for assigning adjusters to
 * insurance claims. The worker receives claim ID and claim type from process variables, delegates
 * the assignment to {@link AdjusterService}, and sets the assigned adjuster ID as a process
 * variable for the BPMN workflow.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Topic name verification
 *   <li>Happy path scenarios with different claim types
 *   <li>Input validation and error handling
 *   <li>Service response validation for EmployeeDto
 *   <li>Process variable extraction and setting
 *   <li>Edge cases and error scenarios
 * </ul>
 *
 * The worker now expects {@link EmployeeDto} from the service instead of ClaimDto,
 * which represents the assigned adjuster information.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssignAdjusterWorker Tests")
class AssignAdjusterWorkerTest {

  @Mock private AdjusterService adjusterService;
  @Mock private ExternalTask externalTask;
  @Mock private ExternalTaskService externalTaskService;

  @Captor private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private AssignAdjusterWorker worker;

  @BeforeEach
  void setUp() {
    worker = new AssignAdjusterWorker(adjusterService);
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
      assertThat(topicName).isEqualTo("insurance.claim.assign-adjuster");
    }
  }

  @Nested
  @DisplayName("Happy Path Tests")
  class HappyPathTests {

    @ParameterizedTest(name = "Should successfully assign adjuster for {0} claim")
    @CsvSource({
      "AUTO, 123, 456",
      "HOME, 789, 101",
      "HEALTH, 999, 202"
    })
    @DisplayName("Should successfully assign adjuster for different claim types")
    void shouldSuccessfullyAssignAdjusterForClaimTypes(String claimTypeString, Long claimId, Long expectedAdjusterId) {
      // Given
      ClaimType claimType = ClaimType.valueOf(claimTypeString);

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("John");
      mockEmployeeDto.setLastName("Doe");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimTypeString);
      when(adjusterService.assignAdjuster(claimType, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(adjusterService).assignAdjuster(claimType, claimId);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, expectedAdjusterId);
    }

    @Test
    @DisplayName("Should handle lowercase claim type")
    void shouldHandleLowercaseClaimType() {
      // Given
      Long claimId = 555L;
      String claimType = "auto"; // lowercase
      Long expectedAdjusterId = 666L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("Jane");
      mockEmployeeDto.setLastName("Smith");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(adjusterService).assignAdjuster(ClaimType.AUTO, claimId);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, expectedAdjusterId);
    }

    @Test
    @DisplayName("Should handle mixed case claim type")
    void shouldHandleMixedCaseClaimType() {
      // Given
      Long claimId = 777L;
      String claimType = "HoMe"; // mixed case
      Long expectedAdjusterId = 888L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("Bob");
      mockEmployeeDto.setLastName("Wilson");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HOME, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(adjusterService).assignAdjuster(ClaimType.HOME, claimId);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, expectedAdjusterId);
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @Test
    @DisplayName("Should throw exception when claim ID is null")
    void shouldThrowExceptionWhenClaimIdIsNull() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(null);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Process variable 'claim_id' is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception for invalid claim type values")
    void shouldThrowExceptionForInvalidClaimTypeValues(String claimTypeValue) {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimTypeValue);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Process variable 'claim_type' is required");
    }

    @Test
    @DisplayName("Should throw exception when claim type is invalid")
    void shouldThrowExceptionWhenClaimTypeIsInvalid() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("INVALID_TYPE");

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid claim type: INVALID_TYPE")
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception when both claim ID and type are null")
    void shouldThrowExceptionWhenBothClaimIdAndTypeAreNull() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(null);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Process variable 'claim_id' is required");
    }
  }

  @Nested
  @DisplayName("Service Response Validation Tests")
  class ServiceResponseValidationTests {

    @Test
    @DisplayName("Should throw exception when service returns null EmployeeDto")
    void shouldThrowExceptionWhenServiceReturnsNullEmployeeDto() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId)).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Adjuster assignment failed for claim: " + claimId);
    }

    @Test
    @DisplayName("Should throw exception when EmployeeDto has null ID")
    void shouldThrowExceptionWhenEmployeeDtoHasNullId() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(null); // null adjuster ID

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Adjuster assignment failed for claim: " + claimId);
    }

    @Test
    @DisplayName("Should handle valid adjuster ID of zero")
    void shouldHandleValidAdjusterIdOfZero() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      Long adjusterIdZero = 0L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(adjusterIdZero);
      mockEmployeeDto.setFirstName("Zero");
      mockEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, adjusterIdZero);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle AdjusterService exception")
    void shouldHandleAdjusterServiceException() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenThrow(new RuntimeException("No available adjusters"));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("No available adjusters");
    }

    @Test
    @DisplayName("Should handle database connection failure")
    void shouldHandleDatabaseConnectionFailure() {
      // Given
      Long claimId = 123L;
      String claimType = "HOME";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HOME, claimId))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Database connection failed");
    }

    @Test
    @DisplayName("Should handle unexpected runtime exceptions")
    void shouldHandleUnexpectedRuntimeExceptions() {
      // Given
      Long claimId = 123L;
      String claimType = "HEALTH";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HEALTH, claimId))
          .thenThrow(new RuntimeException("Unexpected error"));

      // When & Then
      assertThatThrownBy(() -> worker.executeBusinessLogic(externalTask, externalTaskService))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Unexpected error");
    }
  }

  @Nested
  @DisplayName("Process Variable Handling Tests")
  class ProcessVariableHandlingTests {

    @Test
    @DisplayName("Should correctly extract and use claim ID from external task")
    void shouldCorrectlyExtractAndUseClaimIdFromExternalTask() {
      // Given
      Long claimId = 12345L;
      String claimType = "AUTO";
      Long expectedAdjusterId = 67890L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("Test");
      mockEmployeeDto.setLastName("User");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTask).getVariable(ProcInstVars.CLAIM_ID);
      verify(adjusterService).assignAdjuster(ClaimType.AUTO, claimId);
    }

    @Test
    @DisplayName("Should correctly extract and use claim type from external task")
    void shouldCorrectlyExtractAndUseClaimTypeFromExternalTask() {
      // Given
      Long claimId = 123L;
      String claimType = "HOME";
      Long expectedAdjusterId = 456L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("Home");
      mockEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HOME, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTask).getVariable(ProcInstVars.CLAIM_TYPE);
      verify(adjusterService).assignAdjuster(ClaimType.HOME, claimId);
    }

    @Test
    @DisplayName("Should set adjuster ID process variable correctly")
    void shouldSetAdjusterIdProcessVariableCorrectly() {
      // Given
      Long claimId = 999L;
      String claimType = "AUTO";
      Long expectedAdjusterId = 111L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(expectedAdjusterId);
      mockEmployeeDto.setFirstName("John");
      mockEmployeeDto.setLastName("Smith");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsOnlyKeys(ProcInstVars.ADJUSTER_ID, ProcInstVars.ADJUSTER_NAME)
          .containsEntry(ProcInstVars.ADJUSTER_ID, expectedAdjusterId)
          .containsEntry(ProcInstVars.ADJUSTER_NAME, "John Smith");
    }

    @Test
    @DisplayName("Should handle large claim ID values")
    void shouldHandleLargeClaimIdValues() {
      // Given
      Long largeClaimId = Long.MAX_VALUE;
      String claimType = "AUTO";
      Long adjusterIdValue = 123L;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(adjusterIdValue);
      mockEmployeeDto.setFirstName("Large");
      mockEmployeeDto.setLastName("Claim");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(largeClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, largeClaimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(adjusterService).assignAdjuster(ClaimType.AUTO, largeClaimId);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, adjusterIdValue);
    }

    @Test
    @DisplayName("Should handle large adjuster ID values")
    void shouldHandleLargeAdjusterIdValues() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      Long largeAdjusterId = Long.MAX_VALUE;

      EmployeeDto mockEmployeeDto = new EmployeeDto();
      mockEmployeeDto.setId(largeAdjusterId);
      mockEmployeeDto.setFirstName("Large");
      mockEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockEmployeeDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.ADJUSTER_ID, largeAdjusterId);
    }
  }

  @Nested
  @DisplayName("Integration-like Tests")
  class IntegrationLikeTests {

    @Test
    @DisplayName("Should process multiple claim types sequentially")
    void shouldProcessMultipleClaimTypesSequentially() {
      // Test AUTO
      Long autoClaimId = 1L;
      EmployeeDto autoEmployeeDto = new EmployeeDto();
      autoEmployeeDto.setId(100L);
      autoEmployeeDto.setFirstName("Auto");
      autoEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(autoClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
      when(adjusterService.assignAdjuster(ClaimType.AUTO, autoClaimId))
          .thenReturn(autoEmployeeDto);

      worker.executeBusinessLogic(externalTask, externalTaskService);
      verify(adjusterService).assignAdjuster(ClaimType.AUTO, autoClaimId);

      // Test HOME
      Long homeClaimId = 2L;
      EmployeeDto homeEmployeeDto = new EmployeeDto();
      homeEmployeeDto.setId(200L);
      homeEmployeeDto.setFirstName("Home");
      homeEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(homeClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("HOME");
      when(adjusterService.assignAdjuster(ClaimType.HOME, homeClaimId))
          .thenReturn(homeEmployeeDto);

      worker.executeBusinessLogic(externalTask, externalTaskService);
      verify(adjusterService).assignAdjuster(ClaimType.HOME, homeClaimId);

      // Test HEALTH
      Long healthClaimId = 3L;
      EmployeeDto healthEmployeeDto = new EmployeeDto();
      healthEmployeeDto.setId(300L);
      healthEmployeeDto.setFirstName("Health");
      healthEmployeeDto.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(healthClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("HEALTH");
      when(adjusterService.assignAdjuster(ClaimType.HEALTH, healthClaimId))
          .thenReturn(healthEmployeeDto);

      worker.executeBusinessLogic(externalTask, externalTaskService);
      verify(adjusterService).assignAdjuster(ClaimType.HEALTH, healthClaimId);
    }

    @Test
    @DisplayName("Should handle same claim with different adjusters")
    void shouldHandleSameClaimWithDifferentAdjusters() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      Long firstAdjusterId = 456L;
      Long secondAdjusterId = 789L;

      EmployeeDto firstAssignment = new EmployeeDto();
      firstAssignment.setId(firstAdjusterId);
      firstAssignment.setFirstName("First");
      firstAssignment.setLastName("Adjuster");

      EmployeeDto secondAssignment = new EmployeeDto();
      secondAssignment.setId(secondAdjusterId);
      secondAssignment.setFirstName("Second");
      secondAssignment.setLastName("Adjuster");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(firstAssignment)
          .thenReturn(secondAssignment);

      // First assignment
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Second assignment (reassignment scenario)
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then - verify both calls happened
      verify(externalTaskService, org.mockito.Mockito.times(2))
          .complete(eq(externalTask), variablesCaptor.capture());

      // Verify both captured values
      java.util.List<Map<String, Object>> allValues = variablesCaptor.getAllValues();
      assertThat(allValues).hasSize(2);
      assertThat(allValues.get(0)).containsEntry(ProcInstVars.ADJUSTER_ID, firstAdjusterId);
      assertThat(allValues.get(1)).containsEntry(ProcInstVars.ADJUSTER_ID, secondAdjusterId);
    }
  }
}

