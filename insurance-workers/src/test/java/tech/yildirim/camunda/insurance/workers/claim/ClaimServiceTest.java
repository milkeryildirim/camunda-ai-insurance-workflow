package tech.yildirim.camunda.insurance.workers.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import tech.yildirim.aiinsurance.api.generated.clients.ClaimsApiClient;
import tech.yildirim.aiinsurance.api.generated.clients.EmployeesApiClient;
import tech.yildirim.aiinsurance.api.generated.model.AssignAdjusterRequestDto;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.EmploymentTypeEnum;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.SpecializationAreaEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimService Tests")
class ClaimServiceTest {

  @Mock
  private ObjectProvider<ClaimsApiClient> claimsApiClientProvider;

  @Mock
  private ObjectProvider<EmployeesApiClient> employeesApiClientProvider;

  @Mock
  private ClaimsApiClient claimsApiClient;

  @Mock
  private EmployeesApiClient employeesApiClient;

  private ClaimService claimService;

  @BeforeEach
  void setUp() {
    claimService = new ClaimService(claimsApiClientProvider, employeesApiClientProvider);
    // Use lenient() to avoid UnnecessaryStubbingException for unused stubs
    lenient().when(claimsApiClientProvider.getIfAvailable()).thenReturn(claimsApiClient);
    lenient().when(employeesApiClientProvider.getIfAvailable()).thenReturn(employeesApiClient);
  }

  @Nested
  @DisplayName("Auto Claim Tests")
  class AutoClaimTests {

    @Test
    @DisplayName("Should create auto claim successfully")
    void shouldCreateAutoClaimSuccessfully() {
      // Given
      AutoClaimDto inputClaim = createAutoClaimDto(null, 1001L);
      AutoClaimDto createdClaim = createAutoClaimDto(1L, 1001L);
      when(claimsApiClient.createAutoClaim(inputClaim))
          .thenReturn(new ResponseEntity<>(createdClaim, HttpStatus.CREATED));

      // When
      AutoClaimDto result = claimService.createAutoClaim(inputClaim);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getPolicyId()).isEqualTo(1001L);
      verify(claimsApiClient).createAutoClaim(inputClaim);
    }

    @Test
    @DisplayName("Should throw exception when auto claim data is null")
    void shouldThrowExceptionWhenAutoClaimDataIsNull() {
      // When & Then
      assertThatThrownBy(() -> claimService.createAutoClaim(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Auto claim data cannot be null");
    }

    @Test
    @DisplayName("Should return null when ClaimsApiClient is not available")
    void shouldReturnNullWhenClaimsApiClientNotAvailable() {
      // Given
      when(claimsApiClientProvider.getIfAvailable()).thenReturn(null);
      AutoClaimDto inputClaim = createAutoClaimDto(null, 1001L);

      // When
      AutoClaimDto result = claimService.createAutoClaim(inputClaim);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when API call fails")
    void shouldReturnNullWhenApiCallFails() {
      // Given
      AutoClaimDto inputClaim = createAutoClaimDto(null, 1001L);
      when(claimsApiClient.createAutoClaim(inputClaim))
          .thenThrow(new RuntimeException("API Error"));

      // When
      AutoClaimDto result = claimService.createAutoClaim(inputClaim);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should get auto claim by ID successfully")
    void shouldGetAutoClaimByIdSuccessfully() {
      // Given
      Long claimId = 1L;
      AutoClaimDto expectedClaim = createAutoClaimDto(claimId, 1001L);
      when(claimsApiClient.getAutoClaimById(claimId))
          .thenReturn(new ResponseEntity<>(expectedClaim, HttpStatus.OK));

      // When
      AutoClaimDto result = claimService.getAutoClaimById(claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).getAutoClaimById(claimId);
    }

    @Test
    @DisplayName("Should throw exception when claim ID is null or negative")
    void shouldThrowExceptionWhenClaimIdIsInvalid() {
      // When & Then
      assertThatThrownBy(() -> claimService.getAutoClaimById(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim ID cannot be null or negative");

      assertThatThrownBy(() -> claimService.getAutoClaimById(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Claim ID cannot be null or negative");
    }

    @Test
    @DisplayName("Should update auto claim successfully")
    void shouldUpdateAutoClaimSuccessfully() {
      // Given
      Long claimId = 1L;
      AutoClaimDto updateData = createAutoClaimDto(claimId, 1001L);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId, 1001L);
      when(claimsApiClient.updateAutoClaim(claimId, updateData))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      AutoClaimDto result = claimService.updateAutoClaim(claimId, updateData);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).updateAutoClaim(claimId, updateData);
    }

    @Test
    @DisplayName("Should assign adjuster to auto claim successfully")
    void shouldAssignAdjusterToAutoClaimSuccessfully() {
      // Given
      Long claimId = 1L;
      EmployeeDto adjuster = createAdjusterDto(100L, SpecializationAreaEnum.AUTO);
      AutoClaimDto updatedClaim = createAutoClaimDto(claimId, 1001L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.AUTO.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimsApiClient.assignAdjusterToAutoClaim(eq(claimId), any(AssignAdjusterRequestDto.class)))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      AutoClaimDto result = claimService.assignAdjusterToAutoClaim(claimId);

      // Then
      assertThat(result).isNotNull();
      verify(employeesApiClient).getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.AUTO.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimsApiClient).assignAdjusterToAutoClaim(eq(claimId), any(AssignAdjusterRequestDto.class));
    }

    @Test
    @DisplayName("Should return null when no adjusters available")
    void shouldReturnNullWhenNoAdjustersAvailable() {
      // Given
      Long claimId = 1L;
      when(employeesApiClient.getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.AUTO.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(Collections.emptyList(), HttpStatus.OK));

      // When
      AutoClaimDto result = claimService.assignAdjusterToAutoClaim(claimId);

      // Then
      assertThat(result).isNull();
      verify(claimsApiClient, never()).assignAdjusterToAutoClaim(any(), any());
    }
  }

  @Nested
  @DisplayName("Health Claim Tests")
  class HealthClaimTests {

    @Test
    @DisplayName("Should create health claim successfully")
    void shouldCreateHealthClaimSuccessfully() {
      // Given
      HealthClaimDto inputClaim = createHealthClaimDto(null, 1002L);
      HealthClaimDto createdClaim = createHealthClaimDto(2L, 1002L);
      when(claimsApiClient.createHealthClaim(inputClaim))
          .thenReturn(new ResponseEntity<>(createdClaim, HttpStatus.CREATED));

      // When
      HealthClaimDto result = claimService.createHealthClaim(inputClaim);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(2L);
      assertThat(result.getPolicyId()).isEqualTo(1002L);
      verify(claimsApiClient).createHealthClaim(inputClaim);
    }

    @Test
    @DisplayName("Should throw exception when health claim data is null")
    void shouldThrowExceptionWhenHealthClaimDataIsNull() {
      // When & Then
      assertThatThrownBy(() -> claimService.createHealthClaim(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Health claim data cannot be null");
    }

    @Test
    @DisplayName("Should get health claim by ID successfully")
    void shouldGetHealthClaimByIdSuccessfully() {
      // Given
      Long claimId = 2L;
      HealthClaimDto expectedClaim = createHealthClaimDto(claimId, 1002L);
      when(claimsApiClient.getHealthClaimById(claimId))
          .thenReturn(new ResponseEntity<>(expectedClaim, HttpStatus.OK));

      // When
      HealthClaimDto result = claimService.getHealthClaimById(claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).getHealthClaimById(claimId);
    }

    @Test
    @DisplayName("Should update health claim successfully")
    void shouldUpdateHealthClaimSuccessfully() {
      // Given
      Long claimId = 2L;
      HealthClaimDto updateData = createHealthClaimDto(claimId, 1002L);
      HealthClaimDto updatedClaim = createHealthClaimDto(claimId, 1002L);
      when(claimsApiClient.updateHealthClaim(claimId, updateData))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      HealthClaimDto result = claimService.updateHealthClaim(claimId, updateData);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).updateHealthClaim(claimId, updateData);
    }

    @Test
    @DisplayName("Should assign adjuster to health claim successfully")
    void shouldAssignAdjusterToHealthClaimSuccessfully() {
      // Given
      Long claimId = 2L;
      EmployeeDto adjuster = createAdjusterDto(101L, SpecializationAreaEnum.HEALTH);
      HealthClaimDto updatedClaim = createHealthClaimDto(claimId, 1002L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.HEALTH.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimsApiClient.assignAdjusterToHealthClaim(eq(claimId), any(AssignAdjusterRequestDto.class)))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      HealthClaimDto result = claimService.assignAdjusterToHealthClaim(claimId);

      // Then
      assertThat(result).isNotNull();
      verify(employeesApiClient).getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.HEALTH.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimsApiClient).assignAdjusterToHealthClaim(eq(claimId), any(AssignAdjusterRequestDto.class));
    }
  }

  @Nested
  @DisplayName("Home Claim Tests")
  class HomeClaimTests {

    @Test
    @DisplayName("Should create home claim successfully")
    void shouldCreateHomeClaimSuccessfully() {
      // Given
      HomeClaimDto inputClaim = createHomeClaimDto(null, 1003L);
      HomeClaimDto createdClaim = createHomeClaimDto(3L, 1003L);
      when(claimsApiClient.createHomeClaim(inputClaim))
          .thenReturn(new ResponseEntity<>(createdClaim, HttpStatus.CREATED));

      // When
      HomeClaimDto result = claimService.createHomeClaim(inputClaim);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(3L);
      assertThat(result.getPolicyId()).isEqualTo(1003L);
      verify(claimsApiClient).createHomeClaim(inputClaim);
    }

    @Test
    @DisplayName("Should throw exception when home claim data is null")
    void shouldThrowExceptionWhenHomeClaimDataIsNull() {
      // When & Then
      assertThatThrownBy(() -> claimService.createHomeClaim(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Home claim data cannot be null");
    }

    @Test
    @DisplayName("Should get home claim by ID successfully")
    void shouldGetHomeClaimByIdSuccessfully() {
      // Given
      Long claimId = 3L;
      HomeClaimDto expectedClaim = createHomeClaimDto(claimId, 1003L);
      when(claimsApiClient.getHomeClaimById(claimId))
          .thenReturn(new ResponseEntity<>(expectedClaim, HttpStatus.OK));

      // When
      HomeClaimDto result = claimService.getHomeClaimById(claimId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).getHomeClaimById(claimId);
    }

    @Test
    @DisplayName("Should update home claim successfully")
    void shouldUpdateHomeClaimSuccessfully() {
      // Given
      Long claimId = 3L;
      HomeClaimDto updateData = createHomeClaimDto(claimId, 1003L);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId, 1003L);
      when(claimsApiClient.updateHomeClaim(claimId, updateData))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      HomeClaimDto result = claimService.updateHomeClaim(claimId, updateData);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(claimId);
      verify(claimsApiClient).updateHomeClaim(claimId, updateData);
    }

    @Test
    @DisplayName("Should assign adjuster to home claim successfully")
    void shouldAssignAdjusterToHomeClaimSuccessfully() {
      // Given
      Long claimId = 3L;
      EmployeeDto adjuster = createAdjusterDto(102L, SpecializationAreaEnum.HOME);
      HomeClaimDto updatedClaim = createHomeClaimDto(claimId, 1003L);

      when(employeesApiClient.getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.HOME.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue()))
          .thenReturn(new ResponseEntity<>(List.of(adjuster), HttpStatus.OK));

      when(claimsApiClient.assignAdjusterToHomeClaim(eq(claimId), any(AssignAdjusterRequestDto.class)))
          .thenReturn(new ResponseEntity<>(updatedClaim, HttpStatus.OK));

      // When
      HomeClaimDto result = claimService.assignAdjusterToHomeClaim(claimId);

      // Then
      assertThat(result).isNotNull();
      verify(employeesApiClient).getAvailableAdjustersBySpecialization(
          SpecializationAreaEnum.HOME.getValue(),
          EmploymentTypeEnum.EXTERNAL.getValue());
      verify(claimsApiClient).assignAdjusterToHomeClaim(eq(claimId), any(AssignAdjusterRequestDto.class));
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle API client unavailability gracefully")
    void shouldHandleApiClientUnavailabilityGracefully() {
      // Given - Override the lenient stubs for this specific test
      when(claimsApiClientProvider.getIfAvailable()).thenReturn(null);

      // When
      AutoClaimDto autoResult = claimService.getAutoClaimById(1L);
      HealthClaimDto healthResult = claimService.getHealthClaimById(2L);
      HomeClaimDto homeResult = claimService.getHomeClaimById(3L);

      // Then
      assertThat(autoResult).isNull();
      assertThat(healthResult).isNull();
      assertThat(homeResult).isNull();
    }

    @Test
    @DisplayName("Should handle null response from API gracefully")
    void shouldHandleNullResponseFromApiGracefully() {
      // Given
      when(claimsApiClient.getAutoClaimById(1L)).thenReturn(null);

      // When
      AutoClaimDto result = claimService.getAutoClaimById(1L);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle API exceptions gracefully")
    void shouldHandleApiExceptionsGracefully() {
      // Given
      when(claimsApiClient.getAutoClaimById(1L))
          .thenThrow(new RuntimeException("Network error"));

      // When
      AutoClaimDto result = claimService.getAutoClaimById(1L);

      // Then
      assertThat(result).isNull();
    }
  }

  // Helper methods for creating test data
  private AutoClaimDto createAutoClaimDto(Long id, Long policyId) {
    AutoClaimDto claim = new AutoClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(new BigDecimal("5000.00"));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Test auto claim");
    return claim;
  }

  private HealthClaimDto createHealthClaimDto(Long id, Long policyId) {
    HealthClaimDto claim = new HealthClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(new BigDecimal("2000.00"));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Test health claim");
    return claim;
  }

  private HomeClaimDto createHomeClaimDto(Long id, Long policyId) {
    HomeClaimDto claim = new HomeClaimDto();
    claim.setId(id);
    claim.setPolicyId(policyId);
    claim.setEstimatedAmount(new BigDecimal("10000.00"));
    claim.setDateOfIncident(LocalDate.now());
    claim.setDescription("Test home claim");
    return claim;
  }

  private EmployeeDto createAdjusterDto(Long id, SpecializationAreaEnum specialization) {
    EmployeeDto employee = new EmployeeDto();
    employee.setId(id);
    employee.setFirstName("John");
    employee.setLastName("Doe");
    employee.setEmail("john.doe@example.com");
    employee.setSpecializationArea(specialization);
    employee.setEmploymentType(EmploymentTypeEnum.EXTERNAL);
    return employee;
  }
}
