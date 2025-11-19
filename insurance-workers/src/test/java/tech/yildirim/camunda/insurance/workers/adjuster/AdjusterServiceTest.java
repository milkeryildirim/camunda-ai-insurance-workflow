package tech.yildirim.camunda.insurance.workers.adjuster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tech.yildirim.aiinsurance.api.generated.clients.EmployeesApiClient;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.EmploymentTypeEnum;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.SpecializationAreaEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;

/**
 * Unit tests for {@link AdjusterService}.
 *
 * <p>Tests cover all scenarios including successful adjuster assignment, error handling for
 * different claim types, edge cases with null/invalid inputs, and API unavailability scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdjusterService Tests")
class AdjusterServiceTest {

  @Mock private ObjectProvider<EmployeesApiClient> employeesApiClientProvider;

  @Mock private EmployeesApiClient employeesApiClient;

  @Mock private ClaimService claimService;

  private AdjusterService adjusterService;

  @BeforeEach
  void setUp() {
    adjusterService = new AdjusterService(employeesApiClientProvider, claimService);
    // Use lenient() to avoid UnnecessaryStubbingException for unused stubs
    lenient().when(employeesApiClientProvider.getIfAvailable()).thenReturn(employeesApiClient);
  }

  @Nested
  @DisplayName("Auto Claim Adjuster Assignment Tests")
  class AutoClaimAdjusterTests {

    @Test
    @DisplayName("Should assign adjuster to auto claim successfully")
    void shouldAssignAdjusterToAutoClaimSuccessfully() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;
      EmployeeDto adjuster = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);
      AutoClaimDto assignedClaim = createAutoClaimDto(claimId, 1001L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimService.assignAdjusterToAutoClaim(claimId)).thenReturn(assignedClaim);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(employeesApiClient)
          .getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimService).assignAdjusterToAutoClaim(claimId);
    }

    @Test
    @DisplayName("Should return null when no auto adjusters are available")
    void shouldReturnNullWhenNoAutoAdjustersAvailable() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(Collections.emptyList(), HttpStatus.OK));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(employeesApiClient)
          .getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimService, never()).assignAdjusterToAutoClaim(any());
    }

    @Test
    @DisplayName("Should return null when adjuster list is null for auto claim")
    void shouldReturnNullWhenAdjusterListIsNullForAutoClaim() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(claimService, never()).assignAdjusterToAutoClaim(any());
    }

    @Test
    @DisplayName("Should return null when claim service fails to assign adjuster")
    void shouldReturnNullWhenClaimServiceFailsToAssignAdjuster() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;
      EmployeeDto adjuster = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimService.assignAdjusterToAutoClaim(claimId)).thenReturn(null);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(claimService).assignAdjusterToAutoClaim(claimId);
    }
  }

  @Nested
  @DisplayName("Health Claim Adjuster Assignment Tests")
  class HealthClaimAdjusterTests {

    @Test
    @DisplayName("Should assign adjuster to health claim successfully")
    void shouldAssignAdjusterToHealthClaimSuccessfully() {
      // Given
      Long claimId = 2L;
      ClaimType claimType = ClaimType.HEALTH;
      EmployeeDto adjuster = createAdjusterDto(200L, SpecializationAreaEnum.HEALTH);
      HealthClaimDto assignedClaim = createHealthClaimDto(claimId, 1002L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HEALTH.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimService.assignAdjusterToHealthClaim(claimId)).thenReturn(assignedClaim);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(employeesApiClient)
          .getAvailableAdjustersBySpecialization(
              ClaimType.HEALTH.name(), EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimService).assignAdjusterToHealthClaim(claimId);
    }

    @Test
    @DisplayName("Should return null when no health adjusters are available")
    void shouldReturnNullWhenNoHealthAdjustersAvailable() {
      // Given
      Long claimId = 2L;
      ClaimType claimType = ClaimType.HEALTH;

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HEALTH.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(Collections.emptyList(), HttpStatus.OK));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(claimService, never()).assignAdjusterToHealthClaim(any());
    }
  }

  @Nested
  @DisplayName("Home Claim Adjuster Assignment Tests")
  class HomeClaimAdjusterTests {

    @Test
    @DisplayName("Should assign adjuster to home claim successfully")
    void shouldAssignAdjusterToHomeClaimSuccessfully() {
      // Given
      Long claimId = 3L;
      ClaimType claimType = ClaimType.HOME;
      EmployeeDto adjuster = createAdjusterDto(300L, SpecializationAreaEnum.HOME);
      HomeClaimDto assignedClaim = createHomeClaimDto(claimId, 1003L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HOME.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimService.assignAdjusterToHomeClaim(claimId)).thenReturn(assignedClaim);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(employeesApiClient)
          .getAvailableAdjustersBySpecialization(
              ClaimType.HOME.name(), EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimService).assignAdjusterToHomeClaim(claimId);
    }

    @Test
    @DisplayName("Should return null when no home adjusters are available")
    void shouldReturnNullWhenNoHomeAdjustersAvailable() {
      // Given
      Long claimId = 3L;
      ClaimType claimType = ClaimType.HOME;

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HOME.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(Collections.emptyList(), HttpStatus.OK));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(claimService, never()).assignAdjusterToHomeClaim(any());
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @Test
    @DisplayName("Should throw IllegalArgumentException when claim type is null")
    void shouldThrowExceptionWhenClaimTypeIsNull() {
      // When & Then
      assertThatThrownBy(() -> adjusterService.assignAdjuster(null, 1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim type cannot be null");

      verifyNoInteractions(employeesApiClient, claimService);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when claim ID is null")
    void shouldThrowExceptionWhenClaimIdIsNull() {
      // When & Then
      assertThatThrownBy(() -> adjusterService.assignAdjuster(ClaimType.AUTO, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim ID cannot be null or negative");

      verifyNoInteractions(employeesApiClient, claimService);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when claim ID is zero")
    void shouldThrowExceptionWhenClaimIdIsZero() {
      // When & Then
      assertThatThrownBy(() -> adjusterService.assignAdjuster(ClaimType.AUTO, 0L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim ID cannot be null or negative");

      verifyNoInteractions(employeesApiClient, claimService);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when claim ID is negative")
    void shouldThrowExceptionWhenClaimIdIsNegative() {
      // When & Then
      assertThatThrownBy(() -> adjusterService.assignAdjuster(ClaimType.HEALTH, -1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim ID cannot be null or negative");

      verifyNoInteractions(employeesApiClient, claimService);
    }
  }

  @Nested
  @DisplayName("API Unavailability Tests")
  class ApiUnavailabilityTests {

    @Test
    @DisplayName("Should return null when EmployeesApiClient is not available")
    void shouldReturnNullWhenEmployeesApiClientNotAvailable() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;
      when(employeesApiClientProvider.getIfAvailable()).thenReturn(null);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verifyNoInteractions(claimService);
    }

    @Test
    @DisplayName("Should return null when employees API throws exception")
    void shouldReturnNullWhenEmployeesApiThrowsException() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenThrow(new RuntimeException("API connection failed"));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(employeesApiClient)
          .getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue());
      verifyNoInteractions(claimService);
    }

    @Test
    @DisplayName("Should return null when claim service throws exception")
    void shouldReturnNullWhenClaimServiceThrowsException() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;
      EmployeeDto adjuster = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimService.assignAdjusterToAutoClaim(claimId))
          .thenThrow(new RuntimeException("Claim service error"));

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNull();
      verify(claimService).assignAdjusterToAutoClaim(claimId);
    }
  }

  @Nested
  @DisplayName("Multiple Adjusters Selection Tests")
  class MultipleAdjustersSelectionTests {

    @Test
    @DisplayName("Should select first adjuster when multiple adjusters are available")
    void shouldSelectFirstAdjusterWhenMultipleAvailable() {
      // Given
      Long claimId = 1L;
      ClaimType claimType = ClaimType.AUTO;
      EmployeeDto adjuster1 = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);
      EmployeeDto adjuster2 = createAdjusterDto(101L, SpecializationAreaEnum.AUTO);
      EmployeeDto adjuster3 = createAdjusterDto(102L, SpecializationAreaEnum.AUTO);
      AutoClaimDto assignedClaim = createAutoClaimDto(claimId, 1001L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster1, adjuster2, adjuster3), HttpStatus.OK));

      when(claimService.assignAdjusterToAutoClaim(claimId)).thenReturn(assignedClaim);

      // When
      ClaimDto result = adjusterService.assignAdjuster(claimType, claimId);

      // Then
      assertThat(result).isNotNull();
      verify(claimService).assignAdjusterToAutoClaim(claimId);
    }
  }

  @Nested
  @DisplayName("Different Claim Types Tests")
  class DifferentClaimTypesTests {

    @Test
    @DisplayName("Should handle all claim types correctly")
    void shouldHandleAllClaimTypesCorrectly() {
      // Given
      Long autoClaimId = 1L;
      Long healthClaimId = 2L;
      Long homeClaimId = 3L;

      EmployeeDto autoAdjuster = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);
      EmployeeDto healthAdjuster = createAdjusterDto(200L, SpecializationAreaEnum.HEALTH);
      EmployeeDto homeAdjuster = createAdjusterDto(300L, SpecializationAreaEnum.HOME);

      AutoClaimDto autoClaim = createAutoClaimDto(autoClaimId, 1001L);
      HealthClaimDto healthClaim = createHealthClaimDto(healthClaimId, 1002L);
      HomeClaimDto homeClaim = createHomeClaimDto(homeClaimId, 1003L);

      // Setup mocks for AUTO claim
      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.AUTO.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(autoAdjuster), HttpStatus.OK));
      when(claimService.assignAdjusterToAutoClaim(autoClaimId)).thenReturn(autoClaim);

      // Setup mocks for HEALTH claim
      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HEALTH.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(healthAdjuster), HttpStatus.OK));
      when(claimService.assignAdjusterToHealthClaim(healthClaimId)).thenReturn(healthClaim);

      // Setup mocks for HOME claim
      when(employeesApiClient.getAvailableAdjustersBySpecialization(
              ClaimType.HOME.name(), EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(homeAdjuster), HttpStatus.OK));
      when(claimService.assignAdjusterToHomeClaim(homeClaimId)).thenReturn(homeClaim);

      // When & Then
      ClaimDto autoResult = adjusterService.assignAdjuster(ClaimType.AUTO, autoClaimId);
      assertThat(autoResult).isNotNull();
      assertThat(autoResult.getId()).isEqualTo(autoClaimId);

      ClaimDto healthResult = adjusterService.assignAdjuster(ClaimType.HEALTH, healthClaimId);
      assertThat(healthResult).isNotNull();
      assertThat(healthResult.getId()).isEqualTo(healthClaimId);

      ClaimDto homeResult = adjusterService.assignAdjuster(ClaimType.HOME, homeClaimId);
      assertThat(homeResult).isNotNull();
      assertThat(homeResult.getId()).isEqualTo(homeClaimId);

      verify(claimService).assignAdjusterToAutoClaim(autoClaimId);
      verify(claimService).assignAdjusterToHealthClaim(healthClaimId);
      verify(claimService).assignAdjusterToHomeClaim(homeClaimId);
    }
  }

  // Helper methods to create test data

  private EmployeeDto createAdjusterDto(Long id, SpecializationAreaEnum specialization) {
    EmployeeDto employee = new EmployeeDto();
    employee.setId(id);
    employee.setFirstName("Adjuster");
    employee.setLastName("Test" + id);
    employee.setEmail("adjuster" + id + "@example.com");
    employee.setSpecializationArea(specialization);
    employee.setEmploymentType(EmploymentTypeEnum.EXTERNAL);
    return employee;
  }

  private AutoClaimDto createAutoClaimDto(Long id, Long policyId) {
    AutoClaimDto claim = new AutoClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(BigDecimal.valueOf(5000.00));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Auto claim test");
    return claim;
  }

  private HealthClaimDto createHealthClaimDto(Long id, Long policyId) {
    HealthClaimDto claim = new HealthClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(BigDecimal.valueOf(3000.00));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Health claim test");
    return claim;
  }

  private HomeClaimDto createHomeClaimDto(Long id, Long policyId) {
    HomeClaimDto claim = new HomeClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(BigDecimal.valueOf(10000.00));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Home claim test");
    return claim;
  }
}
