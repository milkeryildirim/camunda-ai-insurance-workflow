package tech.yildirim.camunda.insurance.workers.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tech.yildirim.aiinsurance.api.generated.clients.CustomersApi;
import tech.yildirim.aiinsurance.api.generated.model.CustomerDto;

/**
 * Service responsible for interacting with the external customers API and managing customer data.
 *
 * <p>This service encapsulates the logic required to retrieve customer information from the
 * external customers API. It provides caching functionality to avoid repeated remote calls for the
 * same customer and handles API errors gracefully by logging issues and returning null values.
 *
 * <p>Customer data is cached using the "customer" cache with the customer ID as the key. Configure
 * a cache manager (for example Caffeine) and enable caching via {@code @EnableCaching} in your
 * application to take advantage of this feature.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

  private final ObjectProvider<CustomersApi> customersApiProvider;

  /**
   * Retrieves customer information by customer ID with caching support.
   *
   * <p>This method fetches customer data from the external customers API and caches the result to
   * avoid repeated remote calls for the same customer ID. If the API client is not available or an
   * error occurs during the API call, the method returns null and logs the issue.
   *
   * <p>The cache uses the customer ID as the key, so subsequent calls with the same customer ID
   * will return the cached result without making additional API calls, improving performance and
   * reducing load on the external service.
   *
   * @param customerId the unique identifier of the customer to retrieve (must not be null or
   *     negative)
   * @return the customer data transfer object containing customer information, or null if the
   *     customer is not found, the API is unavailable, or an error occurs
   * @throws IllegalArgumentException when {@code customerId} is null or negative
   */
  @Cacheable(cacheNames = "customer", key = "#customerId")
  public CustomerDto getCustomer(Long customerId) {
    if (customerId == null || customerId <= 0) {
      throw new IllegalArgumentException("Customer ID cannot be null or negative");
    }

    final CustomersApi customersApi = customersApiProvider.getIfAvailable();
    if (customersApi == null) {
      log.warn("CustomersApi bean not available; cannot retrieve customer {}", customerId);
      return null;
    }

    try {
      log.debug("Fetching customer data for ID: {}", customerId);
      final ResponseEntity<CustomerDto> response = customersApi.getCustomerById(customerId);
      final CustomerDto customer = response != null ? response.getBody() : null;

      if (customer != null) {
        log.debug("Successfully retrieved customer data for ID: {}", customerId);
      } else {
        log.warn("No customer found for ID: {}", customerId);
      }

      return customer;
    } catch (Exception ex) {
      log.error("Failed to fetch customer {}: {}", customerId, ex.getMessage(), ex);
      return null;
    }
  }
}
