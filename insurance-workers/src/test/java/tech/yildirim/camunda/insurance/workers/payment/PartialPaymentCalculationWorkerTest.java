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
import tech.yildirim.aiinsurance.api.generated.model.ClaimDecisionDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto.StatusEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartialPaymentCalculationWorker Tests")
class PartialPaymentCalculationWorkerTest {

  @Mock
  private ClaimService claimService;

  @Mock
  private ExternalTask externalTask;

  @Mock
  private ExternalTaskService externalTaskService;

  @Captor
  private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private PartialPaymentCalculationWorker partialPaymentCalculationWorker;

  @BeforeEach
  void setUp() {
    partialPaymentCalculationWorker = new PartialPaymentCalculationWorker(claimService);
  }

  @Nested
  @DisplayName("Topic Name Tests")
  class TopicNameTests {

    @Test
    @DisplayName("Should return correct topic name")
    void shouldReturnCorrectTopicName() {
      // When
      String topicName = partialPaymentCalculationWorker.getTopicName();

      // Then
      assertThat(topicName).isEqualTo("insurance.payment.calculate-partial");
    }
  }

  @Nested
  @DisplayName("Payment Percentage Tests")
  class PaymentPercentageTests {

    @Test
    @DisplayName("Should return correct payment percentage")
    void shouldReturnCorrectPaymentPercentage() {
      // When
      int percentage = partialPaymentCalculationWorker.getPaymentPercentage();

      // Then
      assertThat(percentage).isEqualTo(80);
    }
  }

  @Nested
  @DisplayName("Auto Claim Payment Calculation Tests")
  class AutoClaimPaymentCalculationTests {

    @Test
    @DisplayName("Should calculate 80% payment for auto claim successfully")
    void shouldCalculate80PercentPaymentForAutoClaimSuccessfully() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      BigDecimal expectedApprovedAmount = new BigDecimal("800.00");
      Long claimId = 123L;
      String claimType = "AUTO";
      String invoiceDetails = "Repair invoice for vehicle damage";

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      autoClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn(invoiceDetails);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);

      // When
      partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getAutoClaimById(claimId);
      verify(claimService).updateClaimDecision(any(ClaimDecisionDto.class), eq(ClaimType.AUTO));
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.APPROVED_AMOUNT, expectedApprovedAmount);

      // Verify claim decision was updated
      assertThat(claimDecision.getApprovedAmount()).isEqualTo(expectedApprovedAmount);
      assertThat(claimDecision.getAdditionalNotes()).isEqualTo(invoiceDetails);
      assertThat(claimDecision.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle auto claim not found")
    void shouldHandleAutoClaimNotFound() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 999L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Auto claim not found with ID: 999");
      verify(claimService).getAutoClaimById(claimId);
    }

    @Test
    @DisplayName("Should handle auto claim with null decision")
    void shouldHandleAutoClaimWithNullDecision() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      String claimType = "AUTO";

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      autoClaim.setClaimDecision(null);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Claim decision not found for auto claim ID: 123");
    }
  }

  @Nested
  @DisplayName("Home Claim Payment Calculation Tests")
  class HomeClaimPaymentCalculationTests {

    @Test
    @DisplayName("Should calculate 80% payment for home claim successfully")
    void shouldCalculate80PercentPaymentForHomeClaimSuccessfully() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("2500.00");
      BigDecimal expectedApprovedAmount = new BigDecimal("2000.00");
      Long claimId = 456L;
      String claimType = "HOME";
      String invoiceDetails = "Roof repair invoice";

      HomeClaimDto homeClaim = createHomeClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      homeClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn(invoiceDetails);
      when(claimService.getHomeClaimById(claimId)).thenReturn(homeClaim);

      // When
      partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHomeClaimById(claimId);
      verify(claimService).updateClaimDecision(any(ClaimDecisionDto.class), eq(ClaimType.HOME));
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.APPROVED_AMOUNT, expectedApprovedAmount);

      // Verify claim decision was updated
      assertThat(claimDecision.getApprovedAmount()).isEqualTo(expectedApprovedAmount);
      assertThat(claimDecision.getAdditionalNotes()).isEqualTo(invoiceDetails);
      assertThat(claimDecision.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle home claim not found")
    void shouldHandleHomeClaimNotFound() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("2500.00");
      Long claimId = 999L;
      String claimType = "HOME";

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHomeClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Home claim not found with ID: 999");
    }
  }

  @Nested
  @DisplayName("Health Claim Payment Calculation Tests")
  class HealthClaimPaymentCalculationTests {

    @Test
    @DisplayName("Should calculate 80% payment for health claim successfully")
    void shouldCalculate80PercentPaymentForHealthClaimSuccessfully() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("750.00");
      BigDecimal expectedApprovedAmount = new BigDecimal("600.00");
      Long claimId = 789L;
      String claimType = "HEALTH";
      String invoiceDetails = "Medical treatment invoice";

      HealthClaimDto healthClaim = createHealthClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      healthClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn(invoiceDetails);
      when(claimService.getHealthClaimById(claimId)).thenReturn(healthClaim);

      // When
      partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getHealthClaimById(claimId);
      verify(claimService).updateClaimDecision(any(ClaimDecisionDto.class), eq(ClaimType.HEALTH));
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.APPROVED_AMOUNT, expectedApprovedAmount);

      // Verify claim decision was updated
      assertThat(claimDecision.getApprovedAmount()).isEqualTo(expectedApprovedAmount);
      assertThat(claimDecision.getAdditionalNotes()).isEqualTo(invoiceDetails);
      assertThat(claimDecision.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle health claim not found")
    void shouldHandleHealthClaimNotFound() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("750.00");
      Long claimId = 999L;
      String claimType = "HEALTH";

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getHealthClaimById(claimId)).thenReturn(null);

      // When & Then
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Health claim not found with ID: 999");
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @Test
    @DisplayName("Should throw exception when invoice amount is null")
    void shouldThrowExceptionWhenInvoiceAmountIsNull() {
      // Given
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(null);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Invoice amount cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when invoice amount is zero")
    void shouldThrowExceptionWhenInvoiceAmountIsZero() {
      // Given
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(BigDecimal.ZERO);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Invoice amount must be greater than zero");
    }

    @Test
    @DisplayName("Should throw exception when invoice amount is negative")
    void shouldThrowExceptionWhenInvoiceAmountIsNegative() {
      // Given
      BigDecimal negativeAmount = new BigDecimal("-100.00");
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(negativeAmount);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).contains("Invoice amount must be greater than zero");
    }

    @Test
    @DisplayName("Should throw exception when claim ID is null")
    void shouldThrowExceptionWhenClaimIdIsNull() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(null);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when claim type is null")
    void shouldThrowExceptionWhenClaimTypeIsNull() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(null);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when claim type is empty")
    void shouldThrowExceptionWhenClaimTypeIsEmpty() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn("");

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Claim type cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for invalid claim type")
    void shouldThrowExceptionForInvalidClaimType() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      String invalidClaimType = "INVALID";
      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(invalidClaimType);

      // When & Then
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage())
          .contains("Invalid claim type: INVALID")
          .contains("Supported types are: AUTO, HOME, HEALTH");
    }

    @Test
    @DisplayName("Should handle case insensitive claim type")
    void shouldHandleCaseInsensitiveClaimType() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      BigDecimal expectedApprovedAmount = new BigDecimal("800.00");
      Long claimId = 123L;
      String claimType = "auto"; // lowercase

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      autoClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn("Test details");
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);

      // When
      partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(claimService).getAutoClaimById(claimId);
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.APPROVED_AMOUNT, expectedApprovedAmount);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle claim service exception")
    void shouldHandleClaimServiceException() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      String claimType = "AUTO";

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(claimService.getAutoClaimById(claimId))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Database connection failed");
    }

    @Test
    @DisplayName("Should handle task completion failure")
    void shouldHandleTaskCompletionFailure() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("1000.00");
      Long claimId = 123L;
      String claimType = "AUTO";

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      autoClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn("Test details");
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);
      doThrow(new RuntimeException("Task completion failed"))
          .when(externalTaskService).complete(eq(externalTask), any());

      // When & Then
      RuntimeException exception = assertThrows(
          RuntimeException.class,
          () -> partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService)
      );

      assertThat(exception.getMessage()).isEqualTo("Task completion failed");
    }
  }

  @Nested
  @DisplayName("Decimal Precision Tests")
  class DecimalPrecisionTests {

    @Test
    @DisplayName("Should handle decimal calculations with proper rounding")
    void shouldHandleDecimalCalculationsWithProperRounding() {
      // Given
      BigDecimal invoiceAmount = new BigDecimal("333.33");
      BigDecimal expectedApprovedAmount = new BigDecimal("266.66"); // 80% of 333.33 = 266.664, rounded to 266.66
      Long claimId = 123L;
      String claimType = "AUTO";

      AutoClaimDto autoClaim = createAutoClaimDto(claimId);
      ClaimDecisionDto claimDecision = createClaimDecisionDto();
      autoClaim.setClaimDecision(claimDecision);

      when(externalTask.getVariable(ProcInstVars.INVOICE_AMOUNT)).thenReturn(invoiceAmount);
      when(externalTask.getVariable(ProcInstVars.CLAIM_ID)).thenReturn(claimId);
      when(externalTask.getVariable(ProcInstVars.CLAIM_TYPE)).thenReturn(claimType);
      when(externalTask.getVariable(ProcInstVars.INVOICE_DETAILS)).thenReturn("Test details");
      when(claimService.getAutoClaimById(claimId)).thenReturn(autoClaim);

      // When
      partialPaymentCalculationWorker.executeBusinessLogic(externalTask, externalTaskService);

      // Then
      verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

      Map<String, Object> variables = variablesCaptor.getValue();
      assertThat(variables).containsEntry(ProcInstVars.APPROVED_AMOUNT, expectedApprovedAmount);
      assertThat(claimDecision.getApprovedAmount()).isEqualTo(expectedApprovedAmount);
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

  private ClaimDecisionDto createClaimDecisionDto() {
    ClaimDecisionDto decision = new ClaimDecisionDto();
    decision.setId(1L);
    return decision;
  }
}
