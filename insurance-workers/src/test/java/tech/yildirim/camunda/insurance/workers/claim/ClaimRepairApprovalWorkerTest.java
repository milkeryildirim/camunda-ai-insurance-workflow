package tech.yildirim.camunda.insurance.workers.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto.StatusEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimRepairApprovalWorker Tests")
class ClaimRepairApprovalWorkerTest {

  @Mock
  private ClaimService claimService;

  @Mock
  private ExternalTask externalTask;

  @Mock
  private ExternalTaskService externalTaskService;

  @Captor
  private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private ClaimRepairApprovalWorker claimRepairApprovalWorker;

  @BeforeEach
  void setUp() {
    claimRepairApprovalWorker = new ClaimRepairApprovalWorker(claimService);
  }

  @Nested
  @DisplayName("Topic Name Tests")
  class TopicNameTests {

    @Test
    @DisplayName("Should return correct topic name")
    void shouldReturnCorrectTopicName() {
      // When
      String topicName = claimRepairApprovalWorker.getTopicName();

      // Then
      assertThat(topicName).isEqualTo("insurance.claim.repair-approve");
    }
  }

  @Nested
  @DisplayName("Auto Claim Approval Tests")
  class AutoClaimApprovalTests {

    @Test
    @DisplayName("Should successfully approve auto claim repair")
    void shouldSuccessfullyApproveAutoClaimRepair() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      AutoClaimDto autoClaim = createAutoClaimDto(claimId, StatusEnum.SUBMITTED);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);

      // When
      claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getAutoClaimById(claimId);
      assertThat(autoClaim.getStatus()).isEqualTo(StatusEnum.APPROVED);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("repair_approval_completed", true)
          .containsEntry("repair_approval_status", "APPROVED");
    }

    @Test
    @DisplayName("Should handle auto claim not found")
    void shouldHandleAutoClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 999 of type: AUTO");
      verify(claimService).getAutoClaimById(claimId);
      verifyNoMoreInteractions(claimService);
    }

    @Test
    @DisplayName("Should handle auto claim update failure")
    void shouldHandleAutoClaimUpdateFailure() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      AutoClaimDto autoClaim = createAutoClaimDto(claimId, StatusEnum.SUBMITTED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 123 of type: AUTO");
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
    }
  }

  @Nested
  @DisplayName("Home Claim Approval Tests")
  class HomeClaimApprovalTests {

    @Test
    @DisplayName("Should successfully approve home claim repair")
    void shouldSuccessfullyApproveHomeClaimRepair() {
      // Given
      Long claimId = 456L;
      String claimType = "HOME";
      HomeClaimDto homeClaim = createHomeClaimDto(claimId, StatusEnum.SUBMITTED);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);
      when(claimService.updateHomeClaim(claimId, homeClaim)).thenReturn(updatedClaim);

      // When
      claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHomeClaimById(claimId);
      assertThat(homeClaim.getStatus()).isEqualTo(StatusEnum.APPROVED);
      verify(claimService).updateHomeClaim(claimId, homeClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("repair_approval_completed", true)
          .containsEntry("repair_approval_status", "APPROVED");
    }

    @Test
    @DisplayName("Should handle home claim not found")
    void shouldHandleHomeClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "HOME";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHomeClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 999 of type: HOME");
      verify(claimService).getHomeClaimById(claimId);
      verifyNoMoreInteractions(claimService);
    }
  }

  @Nested
  @DisplayName("Health Claim Approval Tests")
  class HealthClaimApprovalTests {

    @Test
    @DisplayName("Should successfully approve health claim repair")
    void shouldSuccessfullyApproveHealthClaimRepair() {
      // Given
      Long claimId = 789L;
      String claimType = "HEALTH";
      HealthClaimDto healthClaim = createHealthClaimDto(claimId, StatusEnum.SUBMITTED);
      HealthClaimDto updatedClaim = createHealthClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHealthClaimById(claimId)).thenReturn(healthClaim);
      when(claimService.updateHealthClaim(claimId, healthClaim)).thenReturn(updatedClaim);

      // When
      claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHealthClaimById(claimId);
      assertThat(healthClaim.getStatus()).isEqualTo(StatusEnum.APPROVED);
      verify(claimService).updateHealthClaim(claimId, healthClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("repair_approval_completed", true)
          .containsEntry("repair_approval_status", "APPROVED");
    }

    @Test
    @DisplayName("Should handle health claim not found")
    void shouldHandleHealthClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "HEALTH";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHealthClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 999 of type: HEALTH");
      verify(claimService).getHealthClaimById(claimId);
      verifyNoMoreInteractions(claimService);
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
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when claim type is null")
    void shouldThrowExceptionWhenClaimTypeIsNull() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(null);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when claim type is empty")
    void shouldThrowExceptionWhenClaimTypeIsEmpty() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("");

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when claim type is whitespace only")
    void shouldThrowExceptionWhenClaimTypeIsWhitespaceOnly() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("   ");

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for invalid claim type")
    void shouldThrowExceptionForInvalidClaimType() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("INVALID");

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Invalid claim type: INVALID")
          .contains("Supported types are: AUTO, HOME, HEALTH");
    }

    @Test
    @DisplayName("Should handle claim type case insensitivity")
    void shouldHandleClaimTypeCaseInsensitivity() {
      // Given
      Long claimId = 123L;
      String claimType = "auto"; // lowercase
      AutoClaimDto autoClaim = createAutoClaimDto(claimId, StatusEnum.SUBMITTED);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);

      // When
      claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
      verify(externalTaskService).complete(eq(externalTask), any());
    }

    @Test
    @DisplayName("Should handle claim type with leading/trailing spaces")
    void shouldHandleClaimTypeWithSpaces() {
      // Given
      Long claimId = 123L;
      String claimType = "  HOME  "; // with spaces
      HomeClaimDto homeClaim = createHomeClaimDto(claimId, StatusEnum.SUBMITTED);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);
      when(claimService.updateHomeClaim(claimId, homeClaim)).thenReturn(updatedClaim);

      // When
      claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHomeClaimById(claimId);
      verify(claimService).updateHomeClaim(claimId, homeClaim);
      verify(externalTaskService).complete(eq(externalTask), any());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle service exception during claim retrieval")
    void shouldHandleServiceExceptionDuringClaimRetrieval() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 123 of type: AUTO");
      verify(claimService).getAutoClaimById(claimId);
    }

    @Test
    @DisplayName("Should handle service exception during claim update")
    void shouldHandleServiceExceptionDuringClaimUpdate() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      AutoClaimDto autoClaim = createAutoClaimDto(claimId, StatusEnum.SUBMITTED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim))
          .thenThrow(new RuntimeException("Update failed"));

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Failed to approve repair for claim ID: 123 of type: AUTO");
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
    }

    @Test
    @DisplayName("Should handle task completion failure")
    void shouldHandleTaskCompletionFailure() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      AutoClaimDto autoClaim = createAutoClaimDto(claimId, StatusEnum.SUBMITTED);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId, StatusEnum.APPROVED);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);
      doThrow(new RuntimeException("Task completion failed"))
          .when(externalTaskService).complete(eq(externalTask), any());

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> claimRepairApprovalWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Task completion failed");
    }
  }

  // Helper methods to create test data
  private AutoClaimDto createAutoClaimDto(Long claimId, StatusEnum status) {
    AutoClaimDto claim = new AutoClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(1L);
    claim.setStatus(status);
    claim.setEstimatedAmount(BigDecimal.valueOf(5000));
    claim.setDateOfIncident(LocalDate.now().minusDays(7));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Auto accident damage");
    return claim;
  }

  private HomeClaimDto createHomeClaimDto(Long claimId, StatusEnum status) {
    HomeClaimDto claim = new HomeClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(2L);
    claim.setStatus(status);
    claim.setEstimatedAmount(BigDecimal.valueOf(10000));
    claim.setDateOfIncident(LocalDate.now().minusDays(3));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Fire damage to property");
    return claim;
  }

  private HealthClaimDto createHealthClaimDto(Long claimId, StatusEnum status) {
    HealthClaimDto claim = new HealthClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(3L);
    claim.setStatus(status);
    claim.setEstimatedAmount(BigDecimal.valueOf(2000));
    claim.setDateOfIncident(LocalDate.now().minusDays(1));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Medical treatment required");
    return claim;
  }
}
