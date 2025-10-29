package tech.yildirim.camunda.insurance.workers.claim;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tech.yildirim.aiinsurance.api.generated.clients.ClaimsApiClient;
import tech.yildirim.aiinsurance.api.generated.clients.EmployeesApiClient;
import tech.yildirim.aiinsurance.api.generated.model.AssignAdjusterRequestDto;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.EmploymentTypeEnum;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.SpecializationAreaEnum;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;

/**
 * Service responsible for managing insurance claims and adjuster assignments.
 *
 * <p>This service encapsulates all claim-related operations including creating, retrieving,
 * updating claims of different types (Auto, Home, Health) and assigning adjusters to claims.
 * It interacts with external APIs for claims and employees management while providing
 * robust error handling and caching for performance optimization.
 *
 * <p>The service handles three types of claims:
 * <ul>
 *   <li><strong>Auto Claims</strong> - Vehicle-related insurance claims</li>
 *   <li><strong>Home Claims</strong> - Property/home insurance claims</li>
 *   <li><strong>Health Claims</strong> - Medical/health insurance claims</li>
 * </ul>
 *
 * <p>Claim data is cached to improve performance and reduce load on external APIs.
 * Configure a cache manager and enable caching via {@code @EnableCaching} in your application.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimService {

  private final ObjectProvider<ClaimsApiClient> claimsApiClientProvider;
  private final ObjectProvider<EmployeesApiClient> employeesApiClientProvider;

  /**
   * Creates a new auto claim in the system.
   *
   * <p>This method submits a new auto claim to the external claims API and returns
   * the created claim with its assigned ID and any additional system-generated fields.
   *
   * @param autoClaimDto the auto claim data to create (must not be null)
   * @return the created auto claim with system-assigned fields, or null if creation fails
   * @throws IllegalArgumentException when {@code autoClaimDto} is null
   */
  public AutoClaimDto createAutoClaim(AutoClaimDto autoClaimDto) {
    if (autoClaimDto == null) {
      throw new IllegalArgumentException("Auto claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot create auto claim");
      return null;
    }

    try {
      log.debug("Creating auto claim for policy: {}", autoClaimDto.getPolicyId());
      final ResponseEntity<AutoClaimDto> response = claimsApiClient.createAutoClaim(autoClaimDto);
      final AutoClaimDto createdClaim = response != null ? response.getBody() : null;
      
      if (createdClaim != null) {
        log.info("Successfully created auto claim with ID: {}", createdClaim.getId());
      } else {
        log.warn("Failed to create auto claim - null response");
      }
      
      return createdClaim;
    } catch (Exception ex) {
      log.error("Failed to create auto claim: {}", ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Creates a new home claim in the system.
   *
   * <p>This method submits a new home claim to the external claims API and returns
   * the created claim with its assigned ID and any additional system-generated fields.
   *
   * @param homeClaimDto the home claim data to create (must not be null)
   * @return the created home claim with system-assigned fields, or null if creation fails
   * @throws IllegalArgumentException when {@code homeClaimDto} is null
   */
  public HomeClaimDto createHomeClaim(HomeClaimDto homeClaimDto) {
    if (homeClaimDto == null) {
      throw new IllegalArgumentException("Home claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot create home claim");
      return null;
    }

    try {
      log.debug("Creating home claim for policy: {}", homeClaimDto.getPolicyId());
      final ResponseEntity<HomeClaimDto> response = claimsApiClient.createHomeClaim(homeClaimDto);
      final HomeClaimDto createdClaim = response != null ? response.getBody() : null;
      
      if (createdClaim != null) {
        log.info("Successfully created home claim with ID: {}", createdClaim.getId());
      } else {
        log.warn("Failed to create home claim - null response");
      }
      
      return createdClaim;
    } catch (Exception ex) {
      log.error("Failed to create home claim: {}", ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Creates a new health claim in the system.
   *
   * <p>This method submits a new health claim to the external claims API and returns
   * the created claim with its assigned ID and any additional system-generated fields.
   *
   * @param healthClaimDto the health claim data to create (must not be null)
   * @return the created health claim with system-assigned fields, or null if creation fails
   * @throws IllegalArgumentException when {@code healthClaimDto} is null
   */
  public HealthClaimDto createHealthClaim(HealthClaimDto healthClaimDto) {
    if (healthClaimDto == null) {
      throw new IllegalArgumentException("Health claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot create health claim");
      return null;
    }

    try {
      log.debug("Creating health claim for policy: {}", healthClaimDto.getPolicyId());
      final ResponseEntity<HealthClaimDto> response = claimsApiClient.createHealthClaim(healthClaimDto);
      final HealthClaimDto createdClaim = response != null ? response.getBody() : null;
      
      if (createdClaim != null) {
        log.info("Successfully created health claim with ID: {}", createdClaim.getId());
      } else {
        log.warn("Failed to create health claim - null response");
      }
      
      return createdClaim;
    } catch (Exception ex) {
      log.error("Failed to create health claim: {}", ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Assigns an available external adjuster to an auto claim.
   *
   * <p>This method finds an available external adjuster specialized in auto claims
   * and assigns them to the specified claim. The assignment is done automatically
   * by selecting the first available adjuster from the pool. The cache for this
   * claim is invalidated after successful assignment to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the auto claim to assign an adjuster to (must not be null or negative)
   * @return the updated auto claim with adjuster assignment, or null if assignment fails
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @CacheEvict(cacheNames = "autoClaim", key = "#claimId", condition = "#result != null")
  public AutoClaimDto assignAdjusterToAutoClaim(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    final EmployeesApiClient employeesApiClient = employeesApiClientProvider.getIfAvailable();
    
    if (claimsApiClient == null || employeesApiClient == null) {
      log.warn("Required API clients not available; cannot assign adjuster to auto claim {}", claimId);
      return null;
    }

    try {
      log.debug("Finding available auto adjuster for claim: {}", claimId);
      final ResponseEntity<List<EmployeeDto>> response = employeesApiClient
          .getAvailableAdjustersBySpecialization(
              SpecializationAreaEnum.AUTO.getValue(), 
              EmploymentTypeEnum.EXTERNAL.getValue());
      
      final List<EmployeeDto> adjusters = response != null ? response.getBody() : null;
      if (adjusters == null || adjusters.isEmpty()) {
        log.warn("No available auto adjusters found for claim: {}", claimId);
        return null;
      }

      final EmployeeDto selectedAdjuster = adjusters.getFirst();
      log.debug("Assigning adjuster {} to auto claim {}", selectedAdjuster.getId(), claimId);

      final ResponseEntity<AutoClaimDto> assignmentResponse = claimsApiClient
          .assignAdjusterToAutoClaim(claimId, new AssignAdjusterRequestDto(selectedAdjuster.getId()));
      
      final AutoClaimDto updatedClaim = assignmentResponse != null ? assignmentResponse.getBody() : null;
      if (updatedClaim != null) {
        log.info("Successfully assigned adjuster {} to auto claim {}", selectedAdjuster.getId(), claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to assign adjuster to auto claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Assigns an available external adjuster to a health claim.
   *
   * <p>This method finds an available external adjuster specialized in health claims
   * and assigns them to the specified claim. The assignment is done automatically
   * by selecting the first available adjuster from the pool. The cache for this
   * claim is invalidated after successful assignment to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the health claim to assign an adjuster to (must not be null or negative)
   * @return the updated health claim with adjuster assignment, or null if assignment fails
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @CacheEvict(cacheNames = "healthClaim", key = "#claimId", condition = "#result != null")
  public HealthClaimDto assignAdjusterToHealthClaim(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    final EmployeesApiClient employeesApiClient = employeesApiClientProvider.getIfAvailable();
    
    if (claimsApiClient == null || employeesApiClient == null) {
      log.warn("Required API clients not available; cannot assign adjuster to health claim {}", claimId);
      return null;
    }

    try {
      log.debug("Finding available health adjuster for claim: {}", claimId);
      final ResponseEntity<List<EmployeeDto>> response = employeesApiClient
          .getAvailableAdjustersBySpecialization(
              SpecializationAreaEnum.HEALTH.getValue(), 
              EmploymentTypeEnum.EXTERNAL.getValue());
      
      final List<EmployeeDto> adjusters = response != null ? response.getBody() : null;
      if (adjusters == null || adjusters.isEmpty()) {
        log.warn("No available health adjusters found for claim: {}", claimId);
        return null;
      }

      final EmployeeDto selectedAdjuster = adjusters.getFirst();
      log.debug("Assigning adjuster {} to health claim {}", selectedAdjuster.getId(), claimId);

      final ResponseEntity<HealthClaimDto> assignmentResponse = claimsApiClient
          .assignAdjusterToHealthClaim(claimId, new AssignAdjusterRequestDto(selectedAdjuster.getId()));
      
      final HealthClaimDto updatedClaim = assignmentResponse != null ? assignmentResponse.getBody() : null;
      if (updatedClaim != null) {
        log.info("Successfully assigned adjuster {} to health claim {}", selectedAdjuster.getId(), claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to assign adjuster to health claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Assigns an available external adjuster to a home claim.
   *
   * <p>This method finds an available external adjuster specialized in home claims
   * and assigns them to the specified claim. The assignment is done automatically
   * by selecting the first available adjuster from the pool. The cache for this
   * claim is invalidated after successful assignment to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the home claim to assign an adjuster to (must not be null or negative)
   * @return the updated home claim with adjuster assignment, or null if assignment fails
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @CacheEvict(cacheNames = "homeClaim", key = "#claimId", condition = "#result != null")
  public HomeClaimDto assignAdjusterToHomeClaim(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    final EmployeesApiClient employeesApiClient = employeesApiClientProvider.getIfAvailable();
    
    if (claimsApiClient == null || employeesApiClient == null) {
      log.warn("Required API clients not available; cannot assign adjuster to home claim {}", claimId);
      return null;
    }

    try {
      log.debug("Finding available home adjuster for claim: {}", claimId);
      final ResponseEntity<List<EmployeeDto>> response = employeesApiClient
          .getAvailableAdjustersBySpecialization(
              SpecializationAreaEnum.HOME.getValue(), 
              EmploymentTypeEnum.EXTERNAL.getValue());
      
      final List<EmployeeDto> adjusters = response != null ? response.getBody() : null;
      if (adjusters == null || adjusters.isEmpty()) {
        log.warn("No available home adjusters found for claim: {}", claimId);
        return null;
      }

      final EmployeeDto selectedAdjuster = adjusters.getFirst();
      log.debug("Assigning adjuster {} to home claim {}", selectedAdjuster.getId(), claimId);

      final ResponseEntity<HomeClaimDto> assignmentResponse = claimsApiClient
          .assignAdjusterToHomeClaim(claimId, new AssignAdjusterRequestDto(selectedAdjuster.getId()));
      
      final HomeClaimDto updatedClaim = assignmentResponse != null ? assignmentResponse.getBody() : null;
      if (updatedClaim != null) {
        log.info("Successfully assigned adjuster {} to home claim {}", selectedAdjuster.getId(), claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to assign adjuster to home claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Retrieves an auto claim by its ID with caching support.
   *
   * <p>This method fetches auto claim data from the external API and caches the result
   * to improve performance on subsequent requests for the same claim.
   *
   * @param claimId the ID of the auto claim to retrieve (must not be null or negative)
   * @return the auto claim data, or null if not found or API unavailable
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @Cacheable(cacheNames = "autoClaim", key = "#claimId")
  public AutoClaimDto getAutoClaimById(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot retrieve auto claim {}", claimId);
      return null;
    }

    try {
      log.debug("Fetching auto claim with ID: {}", claimId);
      final ResponseEntity<AutoClaimDto> response = claimsApiClient.getAutoClaimById(claimId);
      final AutoClaimDto claim = response != null ? response.getBody() : null;
      
      if (claim != null) {
        log.debug("Successfully retrieved auto claim: {}", claimId);
      } else {
        log.warn("Auto claim not found: {}", claimId);
      }
      
      return claim;
    } catch (Exception ex) {
      log.error("Failed to retrieve auto claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Retrieves a health claim by its ID with caching support.
   *
   * <p>This method fetches health claim data from the external API and caches the result
   * to improve performance on subsequent requests for the same claim.
   *
   * @param claimId the ID of the health claim to retrieve (must not be null or negative)
   * @return the health claim data, or null if not found or API unavailable
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @Cacheable(cacheNames = "healthClaim", key = "#claimId")
  public HealthClaimDto getHealthClaimById(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot retrieve health claim {}", claimId);
      return null;
    }

    try {
      log.debug("Fetching health claim with ID: {}", claimId);
      final ResponseEntity<HealthClaimDto> response = claimsApiClient.getHealthClaimById(claimId);
      final HealthClaimDto claim = response != null ? response.getBody() : null;
      
      if (claim != null) {
        log.debug("Successfully retrieved health claim: {}", claimId);
      } else {
        log.warn("Health claim not found: {}", claimId);
      }
      
      return claim;
    } catch (Exception ex) {
      log.error("Failed to retrieve health claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Retrieves a home claim by its ID with caching support.
   *
   * <p>This method fetches home claim data from the external API and caches the result
   * to improve performance on subsequent requests for the same claim.
   *
   * @param claimId the ID of the home claim to retrieve (must not be null or negative)
   * @return the home claim data, or null if not found or API unavailable
   * @throws IllegalArgumentException when {@code claimId} is null or negative
   */
  @Cacheable(cacheNames = "homeClaim", key = "#claimId")
  public HomeClaimDto getHomeClaimById(Long claimId) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot retrieve home claim {}", claimId);
      return null;
    }

    try {
      log.debug("Fetching home claim with ID: {}", claimId);
      final ResponseEntity<HomeClaimDto> response = claimsApiClient.getHomeClaimById(claimId);
      final HomeClaimDto claim = response != null ? response.getBody() : null;
      
      if (claim != null) {
        log.debug("Successfully retrieved home claim: {}", claimId);
      } else {
        log.warn("Home claim not found: {}", claimId);
      }
      
      return claim;
    } catch (Exception ex) {
      log.error("Failed to retrieve home claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Updates an existing auto claim with new data.
   *
   * <p>This method updates an auto claim in the external system with the provided data.
   * Both the claim ID and the updated claim data must be provided. The cache for this
   * claim is invalidated after successful update to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the auto claim to update (must not be null or negative)
   * @param autoClaimDto the updated auto claim data (must not be null)
   * @return the updated auto claim data, or null if update fails
   * @throws IllegalArgumentException when {@code claimId} is null/negative or {@code autoClaimDto} is null
   */
  @CacheEvict(cacheNames = "autoClaim", key = "#claimId", condition = "#result != null")
  public AutoClaimDto updateAutoClaim(Long claimId, AutoClaimDto autoClaimDto) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }
    if (autoClaimDto == null) {
      throw new IllegalArgumentException("Auto claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot update auto claim {}", claimId);
      return null;
    }

    try {
      log.debug("Updating auto claim with ID: {}", claimId);
      final ResponseEntity<AutoClaimDto> response = claimsApiClient.updateAutoClaim(claimId, autoClaimDto);
      final AutoClaimDto updatedClaim = response != null ? response.getBody() : null;
      
      if (updatedClaim != null) {
        log.info("Successfully updated auto claim: {}", claimId);
      } else {
        log.warn("Failed to update auto claim: {}", claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to update auto claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Updates an existing health claim with new data.
   *
   * <p>This method updates a health claim in the external system with the provided data.
   * Both the claim ID and the updated claim data must be provided. The cache for this
   * claim is invalidated after successful update to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the health claim to update (must not be null or negative)
   * @param healthClaimDto the updated health claim data (must not be null)
   * @return the updated health claim data, or null if update fails
   * @throws IllegalArgumentException when {@code claimId} is null/negative or {@code healthClaimDto} is null
   */
  @CacheEvict(cacheNames = "healthClaim", key = "#claimId", condition = "#result != null")
  public HealthClaimDto updateHealthClaim(Long claimId, HealthClaimDto healthClaimDto) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }
    if (healthClaimDto == null) {
      throw new IllegalArgumentException("Health claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot update health claim {}", claimId);
      return null;
    }

    try {
      log.debug("Updating health claim with ID: {}", claimId);
      final ResponseEntity<HealthClaimDto> response = claimsApiClient.updateHealthClaim(claimId, healthClaimDto);
      final HealthClaimDto updatedClaim = response != null ? response.getBody() : null;
      
      if (updatedClaim != null) {
        log.info("Successfully updated health claim: {}", claimId);
      } else {
        log.warn("Failed to update health claim: {}", claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to update health claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Updates an existing home claim with new data.
   *
   * <p>This method updates a home claim in the external system with the provided data.
   * Both the claim ID and the updated claim data must be provided. The cache for this
   * claim is invalidated after successful update to ensure fresh data on subsequent reads.
   *
   * @param claimId the ID of the home claim to update (must not be null or negative)
   * @param homeClaimDto the updated home claim data (must not be null)
   * @return the updated home claim data, or null if update fails
   * @throws IllegalArgumentException when {@code claimId} is null/negative or {@code homeClaimDto} is null
   */
  @CacheEvict(cacheNames = "homeClaim", key = "#claimId", condition = "#result != null")
  public HomeClaimDto updateHomeClaim(Long claimId, HomeClaimDto homeClaimDto) {
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }
    if (homeClaimDto == null) {
      throw new IllegalArgumentException("Home claim data cannot be null");
    }

    final ClaimsApiClient claimsApiClient = claimsApiClientProvider.getIfAvailable();
    if (claimsApiClient == null) {
      log.warn("ClaimsApiClient not available; cannot update home claim {}", claimId);
      return null;
    }

    try {
      log.debug("Updating home claim with ID: {}", claimId);
      final ResponseEntity<HomeClaimDto> response = claimsApiClient.updateHomeClaim(claimId, homeClaimDto);
      final HomeClaimDto updatedClaim = response != null ? response.getBody() : null;
      
      if (updatedClaim != null) {
        log.info("Successfully updated home claim: {}", claimId);
      } else {
        log.warn("Failed to update home claim: {}", claimId);
      }
      
      return updatedClaim;
    } catch (Exception ex) {
      log.error("Failed to update home claim {}: {}", claimId, ex.getMessage(), ex);
      return null;
    }
  }
}
