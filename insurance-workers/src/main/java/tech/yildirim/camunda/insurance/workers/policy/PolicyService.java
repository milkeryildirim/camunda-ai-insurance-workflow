package tech.yildirim.camunda.insurance.workers.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tech.yildirim.aiinsurance.api.generated.clients.PoliciesApiClient;
import tech.yildirim.aiinsurance.api.generated.model.PolicyDto;
import tech.yildirim.aiinsurance.api.generated.model.PolicyDto.StatusEnum;

import java.time.LocalDate;

/**
 * Service responsible for interacting with the external policies API and evaluating policy state.
 *
 * <p>This service encapsulates the logic required to determine whether a given policy number
 * represents an active, non-expired policy. It shields callers from API errors and null values by
 * returning a boolean result and logging problems. The external API client is injected as an
 * optional dependency so this service can be used in environments where the generated client bean
 * is not available (for example during incremental builds or in integration tests).
 *
 * <p>Policy validation results are cached to avoid repeated remote calls for the same policy number.
 * The cache is named "policyValidity" and uses the policy number as the key. Configure a cache manager
 * (for example Caffeine) and enable caching via {@code @EnableCaching} in your application.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

  private final ObjectProvider<PoliciesApiClient> policiesApiClientProvider;

  /**
   * Retrieves policy information from the external API.
   *
   * <p>This method fetches policy data from the external policies API. If the API client is not
   * available, returns null and logs a warning. This method is not cached to allow for different
   * caching strategies on the business methods that use it.
   *
   * @param policyNumber the policy number to retrieve (must not be null or blank)
   * @return the policy data transfer object, or null if not found or API unavailable
   * @throws IllegalArgumentException when {@code policyNumber} is null or blank
   */
  public PolicyDto getPolicyByPolicyNumber(String policyNumber) {
    if (policyNumber == null || policyNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("Policy number cannot be null or empty");
    }

    final PoliciesApiClient policiesApiClient = policiesApiClientProvider.getIfAvailable();
    if (policiesApiClient == null) {
      log.warn("PoliciesApiClient bean not available; cannot retrieve policy {}", policyNumber);
      return null;
    }

    try {
      final ResponseEntity<PolicyDto> response = policiesApiClient.getPolicyByPolicyNumber(policyNumber);
      return response != null ? response.getBody() : null;
    } catch (Exception ex) {
      log.error("Failed to fetch policy {}: {}", policyNumber, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Checks whether the given policy number corresponds to an active and not-yet-expired policy.
   *
   * <p>This method retrieves policy data from the external API and evaluates the policy status
   * and expiration date. Returns false for any errors, missing policies, or invalid policy states.
   * Results are cached to avoid repeated API calls for the same policy number.
   *
   * @param policyNumber the policy number to check (must not be null or blank)
   * @return {@code true} if the policy exists, is ACTIVE and its end date is strictly after the
   *     current date; {@code false} otherwise
   * @throws IllegalArgumentException when {@code policyNumber} is null or blank
   */
  @Cacheable(cacheNames = "policyValidity", key = "#policyNumber")
  public boolean isPolicyValid(String policyNumber) {
    if (policyNumber == null || policyNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("Policy number cannot be null or empty");
    }

    final PolicyDto policyDto = getPolicyByPolicyNumber(policyNumber);

    if (policyDto == null) {
      log.warn("No policy found for policyNumber={}", policyNumber);
      return false;
    }

    final StatusEnum status = policyDto.getStatus();
    final LocalDate endDate = policyDto.getEndDate();

    return StatusEnum.ACTIVE.equals(status)
        && endDate != null
        && endDate.isAfter(LocalDate.now());
  }
}
