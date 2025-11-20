package tech.yildirim.camunda.insurance.workers.adjuster;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tech.yildirim.aiinsurance.api.generated.clients.EmployeesApiClient;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto;
import tech.yildirim.aiinsurance.api.generated.model.EmployeeDto.EmploymentTypeEnum;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;
import tech.yildirim.camunda.insurance.workers.claim.ClaimType;

/**
 * Service responsible for managing insurance adjusters and their assignment to claims.
 *
 * <p>This service handles the business logic for finding and assigning available external adjusters
 * to insurance claims based on their specialization area. It acts as a coordinator between the
 * employee API (for finding adjusters) and the claim service (for assignment).
 *
 * <p>The service supports adjuster assignment for all claim types:
 *
 * <ul>
 *   <li><strong>Auto Claims</strong> - Assigns adjusters specialized in vehicle insurance
 *   <li><strong>Home Claims</strong> - Assigns adjusters specialized in property insurance
 *   <li><strong>Health Claims</strong> - Assigns adjusters specialized in medical insurance
 * </ul>
 *
 * <p>The adjuster selection process prioritizes external adjusters who are currently available and
 * have the appropriate specialization for the claim type. If no suitable adjuster is found, the
 * assignment fails gracefully with appropriate logging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdjusterService {

  private final ObjectProvider<EmployeesApiClient> employeesApiClientProvider;
  private final ClaimService claimService;

  /**
   * Finds and assigns an available external adjuster to a claim based on the claim type.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Validates the provided claim type
   *   <li>Queries the employees API for available external adjusters with matching specialization
   *   <li>Selects the first available adjuster from the pool
   *   <li>Assigns the selected adjuster to the claim via the claim service
   *   <li>Returns the assigned adjuster employee data transfer object
   * </ol>
   *
   * <p>If no adjusters are available for the specified claim type or if the assignment fails, this
   * method returns null and logs appropriate warnings or errors.
   *
   * @param claimType the type of claim requiring adjuster assignment (must not be null)
   * @param claimId the unique identifier of the claim to assign an adjuster to (must not be null or
   *     negative)
   * @return the assigned adjuster employee data transfer object, or null if no adjuster
   *     is available or assignment fails
   * @throws IllegalArgumentException when {@code claimType} is null or {@code claimId} is null or
   *     negative
   */
  public EmployeeDto assignAdjuster(ClaimType claimType, Long claimId) {
    if (claimType == null) {
      throw new IllegalArgumentException("Claim type cannot be null");
    }
    if (claimId == null || claimId <= 0) {
      throw new IllegalArgumentException("Claim ID cannot be null or negative");
    }

    final EmployeesApiClient employeesClient = employeesApiClientProvider.getIfAvailable();
    if (employeesClient == null) {
      log.warn(
          "EmployeesApiClient not available; cannot assign adjuster to {} claim {}",
          claimType,
          claimId);
      return null;
    }

    try {
      log.debug("Finding available {} adjuster for claim: {}", claimType, claimId);

      final ResponseEntity<List<EmployeeDto>> response =
          employeesClient.getAvailableAdjustersBySpecialization(
              claimType.name(), EmploymentTypeEnum.EXTERNAL.getValue());

      final List<EmployeeDto> availableAdjusters = response != null ? response.getBody() : null;

      if (availableAdjusters == null || availableAdjusters.isEmpty()) {
        log.warn("No available {} adjusters found for claim: {}", claimType, claimId);
        return null;
      }

      final EmployeeDto selectedAdjuster = availableAdjusters.getFirst();
      log.debug(
          "Assigning adjuster {} to {} claim {}", selectedAdjuster.getId(), claimType, claimId);

      final ClaimDto assignedClaim = assignAdjusterByClaimType(claimType, claimId);

      if (assignedClaim != null) {
        log.info(
            "Successfully assigned adjuster {} to {} claim {}",
            selectedAdjuster.getId(),
            claimType,
            claimId);
        return selectedAdjuster;
      } else {
        log.warn(
            "Failed to assign adjuster {} to {} claim {}",
            selectedAdjuster.getId(),
            claimType,
            claimId);
        return null;
      }

    } catch (Exception ex) {
      log.error(
          "Failed to assign adjuster to {} claim {}: {}", claimType, claimId, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Delegates the adjuster assignment to the appropriate claim service method based on claim type.
   *
   * <p>This private helper method encapsulates the type-specific assignment logic, routing the
   * request to the correct claim service method based on the claim type (AUTO, HOME, or HEALTH).
   *
   * @param claimType the type of claim to assign the adjuster to
   * @param claimId the unique identifier of the claim
   * @return the updated claim with adjuster assignment, or null if assignment fails
   */
  private ClaimDto assignAdjusterByClaimType(ClaimType claimType, Long claimId) {
    return switch (claimType) {
      case AUTO -> claimService.assignAdjusterToAutoClaim(claimId);
      case HEALTH -> claimService.assignAdjusterToHealthClaim(claimId);
      case HOME -> claimService.assignAdjusterToHomeClaim(claimId);
    };
  }
}
