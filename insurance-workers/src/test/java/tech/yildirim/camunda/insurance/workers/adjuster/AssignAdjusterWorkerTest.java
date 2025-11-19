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
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimTypeString);
      when(adjusterService.assignAdjuster(claimType, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HOME, claimId))
          .thenReturn(mockClaimDto);

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
    @DisplayName("Should throw exception when service returns null ClaimDto")
    void shouldThrowExceptionWhenServiceReturnsNullClaimDto() {
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
    @DisplayName("Should throw exception when ClaimDto has null adjuster ID")
    void shouldThrowExceptionWhenClaimDtoHasNullAdjusterId() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(null); // null adjuster ID

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(adjusterIdZero);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.HOME, claimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(expectedAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

      // When
      worker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsOnlyKeys(ProcInstVars.ADJUSTER_ID)
          .containsEntry(ProcInstVars.ADJUSTER_ID, expectedAdjusterId);
    }

    @Test
    @DisplayName("Should handle large claim ID values")
    void shouldHandleLargeClaimIdValues() {
      // Given
      Long largeClaimId = Long.MAX_VALUE;
      String claimType = "AUTO";
      Long adjusterIdValue = 123L;

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(largeClaimId);
      mockClaimDto.setAssignedAdjusterId(adjusterIdValue);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(largeClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, largeClaimId))
          .thenReturn(mockClaimDto);

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

      ClaimDto mockClaimDto = new ClaimDto();
      mockClaimDto.setId(claimId);
      mockClaimDto.setAssignedAdjusterId(largeAdjusterId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(adjusterService.assignAdjuster(ClaimType.AUTO, claimId))
          .thenReturn(mockClaimDto);

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
      ClaimDto autoClaimDto = new ClaimDto();
      autoClaimDto.setId(autoClaimId);
      autoClaimDto.setAssignedAdjusterId(100L);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(autoClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
      when(adjusterService.assignAdjuster(ClaimType.AUTO, autoClaimId))
          .thenReturn(autoClaimDto);

      worker.executeBusinessLogic(externalTask, externalTaskService);
      verify(adjusterService).assignAdjuster(ClaimType.AUTO, autoClaimId);

      // Test HOME
      Long homeClaimId = 2L;
      ClaimDto homeClaimDto = new ClaimDto();
      homeClaimDto.setId(homeClaimId);
      homeClaimDto.setAssignedAdjusterId(200L);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(homeClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("HOME");
      when(adjusterService.assignAdjuster(ClaimType.HOME, homeClaimId))
          .thenReturn(homeClaimDto);

      worker.executeBusinessLogic(externalTask, externalTaskService);
      verify(adjusterService).assignAdjuster(ClaimType.HOME, homeClaimId);

      // Test HEALTH
      Long healthClaimId = 3L;
      ClaimDto healthClaimDto = new ClaimDto();
      healthClaimDto.setId(healthClaimId);
      healthClaimDto.setAssignedAdjusterId(300L);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(healthClaimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("HEALTH");
      when(adjusterService.assignAdjuster(ClaimType.HEALTH, healthClaimId))
          .thenReturn(healthClaimDto);

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

      ClaimDto firstAssignment = new ClaimDto();
      firstAssignment.setId(claimId);
      firstAssignment.setAssignedAdjusterId(firstAdjusterId);

      ClaimDto secondAssignment = new ClaimDto();
      secondAssignment.setId(claimId);
      secondAssignment.setAssignedAdjusterId(secondAdjusterId);

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

