package tech.yildirim.camunda.insurance.workers.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentExecutionWorker Tests")
class PaymentExecutionWorkerTest {

  @Mock
  private ClaimService claimService;

  @Mock
  private ExternalTask externalTask;

  @Mock
  private ExternalTaskService externalTaskService;

  @Captor
  private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private PaymentExecutionWorker paymentExecutionWorker;

  @BeforeEach
  void setUp() {
    paymentExecutionWorker = new PaymentExecutionWorker(claimService);
  }

  @Nested
  @DisplayName("Topic Name Tests")
  class TopicNameTests {

    @Test
    @DisplayName("Should return correct topic name")
    void shouldReturnCorrectTopicName() {
      // When
      String topicName = paymentExecutionWorker.getTopicName();

      // Then
      assertThat(topicName).isEqualTo("insurance.payment.execute");
    }
  }

  @Nested
  @DisplayName("Auto Claim Payment Execution Tests")
  class AutoClaimPaymentExecutionTests {

    @Test
    @DisplayName("Should execute payment for auto claim successfully")
    void shouldExecutePaymentForAutoClaimSuccessfully() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1500.00");

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId);
      updatedClaim.setPaidAmount(approvedAmount);
      updatedClaim.setStatus(StatusEnum.PAID);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      // Verify claim was updated correctly
      assertThat(autoClaim.getPaidAmount()).isEqualTo(approvedAmount);
      assertThat(autoClaim.getStatus()).isEqualTo(StatusEnum.PAID);

      // Verify process variables
      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("payment_executed", true)
          .containsEntry("paid_amount", approvedAmount)
          .containsEntry("payment_status", "COMPLETED");
    }

    @Test
    @DisplayName("Should handle auto claim not found")
    void shouldHandleAutoClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1500.00");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Auto claim not found with ID: 999");
      verify(claimService).getAutoClaimById(claimId);
    }

    @Test
    @DisplayName("Should handle auto claim update failure")
    void shouldHandleAutoClaimUpdateFailure() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1500.00");

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Failed to update auto claim payment for ID: 123");
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateAutoClaim(claimId, autoClaim);
    }
  }

  @Nested
  @DisplayName("Home Claim Payment Execution Tests")
  class HomeClaimPaymentExecutionTests {

    @Test
    @DisplayName("Should execute payment for home claim successfully")
    void shouldExecutePaymentForHomeClaimSuccessfully() {
      // Given
      Long claimId = 456L;
      String claimType = "HOME";
      BigDecimal approvedAmount = new BigDecimal("3000.00");

      HomeClaimDto homeClaim = createHomeClaimDto(claimId);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId);
      updatedClaim.setPaidAmount(approvedAmount);
      updatedClaim.setStatus(StatusEnum.PAID);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);
      when(claimService.updateHomeClaim(claimId, homeClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHomeClaimById(claimId);
      verify(claimService).updateHomeClaim(claimId, homeClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      // Verify claim was updated correctly
      assertThat(homeClaim.getPaidAmount()).isEqualTo(approvedAmount);
      assertThat(homeClaim.getStatus()).isEqualTo(StatusEnum.PAID);

      // Verify process variables
      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("payment_executed", true)
          .containsEntry("paid_amount", approvedAmount)
          .containsEntry("payment_status", "COMPLETED");
    }

    @Test
    @DisplayName("Should handle home claim not found")
    void shouldHandleHomeClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "HOME";
      BigDecimal approvedAmount = new BigDecimal("3000.00");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHomeClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Home claim not found with ID: 999");
    }
  }

  @Nested
  @DisplayName("Health Claim Payment Execution Tests")
  class HealthClaimPaymentExecutionTests {

    @Test
    @DisplayName("Should execute payment for health claim successfully")
    void shouldExecutePaymentForHealthClaimSuccessfully() {
      // Given
      Long claimId = 789L;
      String claimType = "HEALTH";
      BigDecimal approvedAmount = new BigDecimal("750.50");

      HealthClaimDto healthClaim = createHealthClaimDto(claimId);
      HealthClaimDto updatedClaim = createHealthClaimDto(claimId);
      updatedClaim.setPaidAmount(approvedAmount);
      updatedClaim.setStatus(StatusEnum.PAID);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHealthClaimById(claimId)).thenReturn(healthClaim);
      when(claimService.updateHealthClaim(claimId, healthClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHealthClaimById(claimId);
      verify(claimService).updateHealthClaim(claimId, healthClaim);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      // Verify claim was updated correctly
      assertThat(healthClaim.getPaidAmount()).isEqualTo(approvedAmount);
      assertThat(healthClaim.getStatus()).isEqualTo(StatusEnum.PAID);

      // Verify process variables
      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables)
          .containsEntry("payment_executed", true)
          .containsEntry("paid_amount", approvedAmount)
          .containsEntry("payment_status", "COMPLETED");
    }

    @Test
    @DisplayName("Should handle health claim not found")
    void shouldHandleHealthClaimNotFound() {
      // Given
      Long claimId = 999L;
      String claimType = "HEALTH";
      BigDecimal approvedAmount = new BigDecimal("750.50");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHealthClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Health claim not found with ID: 999");
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

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when claim ID is negative")
    void shouldThrowExceptionWhenClaimIdIsNegative() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(-1L);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Claim ID must be greater than zero, but was: -1");
    }

    @Test
    @DisplayName("Should throw exception when claim ID is zero")
    void shouldThrowExceptionWhenClaimIdIsZero() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(0L);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Claim ID must be greater than zero, but was: 0");
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
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
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
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when claim type is whitespace")
    void shouldThrowExceptionWhenClaimTypeIsWhitespace() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("   ");

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for invalid claim type")
    void shouldThrowExceptionForInvalidClaimType() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("INVALID");
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(new BigDecimal("1000.00"));

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Invalid claim type: INVALID")
          .contains("Supported types are: AUTO, HOME, HEALTH");
    }

    @Test
    @DisplayName("Should throw exception when approved amount is null")
    void shouldThrowExceptionWhenApprovedAmountIsNull() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(null);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Approved amount cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when approved amount is zero")
    void shouldThrowExceptionWhenApprovedAmountIsZero() {
      // Given
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(BigDecimal.ZERO);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Approved amount must be greater than zero, but was: 0");
    }

    @Test
    @DisplayName("Should throw exception when approved amount is negative")
    void shouldThrowExceptionWhenApprovedAmountIsNegative() {
      // Given
      BigDecimal negativeAmount = new BigDecimal("-500.00");
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(123L);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("AUTO");
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(negativeAmount);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Approved amount must be greater than zero, but was: -500.00");
    }

    @Test
    @DisplayName("Should handle case insensitive claim type")
    void shouldHandleCaseInsensitiveClaimType() {
      // Given
      Long claimId = 123L;
      String claimType = "auto"; // lowercase
      BigDecimal approvedAmount = new BigDecimal("1000.00");

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

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
      BigDecimal approvedAmount = new BigDecimal("2000.00");

      HomeClaimDto homeClaim = createHomeClaimDto(claimId);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);
      when(claimService.updateHomeClaim(claimId, homeClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

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
    @DisplayName("Should handle claim service exception during claim retrieval")
    void shouldHandleClaimServiceExceptionDuringClaimRetrieval() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1000.00");

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Database connection failed");
    }

    @Test
    @DisplayName("Should handle claim service exception during claim update")
    void shouldHandleClaimServiceExceptionDuringClaimUpdate() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1000.00");

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim))
          .thenThrow(new RuntimeException("Update operation failed"));

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Update operation failed");
    }

    @Test
    @DisplayName("Should handle task completion failure")
    void shouldHandleTaskCompletionFailure() {
      // Given
      Long claimId = 123L;
      String claimType = "AUTO";
      BigDecimal approvedAmount = new BigDecimal("1000.00");

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      when(claimService.updateAutoClaim(claimId, autoClaim)).thenReturn(updatedClaim);
      doThrow(new RuntimeException("Task completion failed"))
          .when(externalTaskService).complete(eq(externalTask), any());

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Task completion failed");
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle decimal amounts correctly")
    void shouldHandleDecimalAmountsCorrectly() {
      // Given
      Long claimId = 123L;
      String claimType = "HEALTH";
      BigDecimal approvedAmount = new BigDecimal("1234.56");

      HealthClaimDto healthClaim = createHealthClaimDto(claimId);
      HealthClaimDto updatedClaim = createHealthClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHealthClaimById(claimId)).thenReturn(healthClaim);
      when(claimService.updateHealthClaim(claimId, healthClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      assertThat(healthClaim.getPaidAmount()).isEqualTo(approvedAmount);
      assertThat(healthClaim.getStatus()).isEqualTo(StatusEnum.PAID);
    }

    @Test
    @DisplayName("Should handle large payment amounts")
    void shouldHandleLargePaymentAmounts() {
      // Given
      Long claimId = 123L;
      String claimType = "HOME";
      BigDecimal approvedAmount = new BigDecimal("99999.99");

      HomeClaimDto homeClaim = createHomeClaimDto(claimId);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId);

      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.APPROVED_AMOUNT)).thenReturn(approvedAmount);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);
      when(claimService.updateHomeClaim(claimId, homeClaim)).thenReturn(updatedClaim);

      // When
      paymentExecutionWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      assertThat(homeClaim.getPaidAmount()).isEqualTo(approvedAmount);
      assertThat(homeClaim.getStatus()).isEqualTo(StatusEnum.PAID);
    }
  }

  // Helper methods to create test data
  private AutoClaimDto createAutoClaimDto(Long claimId) {
    AutoClaimDto claim = new AutoClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(1L);
    claim.setStatus(StatusEnum.APPROVED);
    claim.setEstimatedAmount(BigDecimal.valueOf(5000));
    claim.setDateOfIncident(LocalDate.now().minusDays(7));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Auto accident damage");
    return claim;
  }

  private HomeClaimDto createHomeClaimDto(Long claimId) {
    HomeClaimDto claim = new HomeClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(2L);
    claim.setStatus(StatusEnum.APPROVED);
    claim.setEstimatedAmount(BigDecimal.valueOf(10000));
    claim.setDateOfIncident(LocalDate.now().minusDays(3));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Fire damage to property");
    return claim;
  }

  private HealthClaimDto createHealthClaimDto(Long claimId) {
    HealthClaimDto claim = new HealthClaimDto();
    claim.setId(claimId);
    claim.setPolicyId(3L);
    claim.setStatus(StatusEnum.APPROVED);
    claim.setEstimatedAmount(BigDecimal.valueOf(2000));
    claim.setDateOfIncident(LocalDate.now().minusDays(1));
    claim.setDateReported(LocalDate.now());
    claim.setDescription("Medical treatment required");
    return claim;
  }
}
