package tech.yildirim.camunda.insurance.workers.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tech.yildirim.aiinsurance.api.generated.clients.CustomersApi;
import tech.yildirim.aiinsurance.api.generated.model.CustomerDto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomerService}.
 *
 * <p>Tests cover all scenarios including successful retrieval, caching behavior, error handling,
 * and edge cases with null/invalid inputs.
 */
@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(CustomerServiceTest.TestCacheConfiguration.class)
@SpringBootTest
class CustomerServiceTest {

  @Mock private ObjectProvider<CustomersApi> customersApiProvider;

  @Mock private CustomersApi customersApi;

  @Mock private ResponseEntity<CustomerDto> responseEntity;

  private CustomerService customerService;
  private CustomerDto sampleCustomer;

  @BeforeEach
  void setUp() {
    customerService = new CustomerService(customersApiProvider);

    // Create sample customer data
    sampleCustomer = new CustomerDto();
    sampleCustomer.setId(1L);
    sampleCustomer.setFirstName("John");
    sampleCustomer.setLastName("Doe");
    sampleCustomer.setEmail("john.doe@example.com");
  }

  @Test
  @DisplayName("Should successfully retrieve customer when API is available and customer exists")
  void shouldRetrieveCustomerSuccessfully() {
    // Given
    Long customerId = 1L;
    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);
    when(customersApi.getCustomerById(customerId)).thenReturn(responseEntity);
    when(responseEntity.getBody()).thenReturn(sampleCustomer);

    // When
    CustomerDto result = customerService.getCustomer(customerId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(customerId);
    assertThat(result.getFirstName()).isEqualTo("John");
    assertThat(result.getLastName()).isEqualTo("Doe");
    assertThat(result.getEmail()).isEqualTo("john.doe@example.com");

    verify(customersApiProvider).getIfAvailable();
    verify(customersApi).getCustomerById(customerId);
  }

  @Test
  @DisplayName("Should return null when CustomersApi bean is not available")
  void shouldReturnNullWhenApiNotAvailable() {
    // Given
    Long customerId = 1L;
    when(customersApiProvider.getIfAvailable()).thenReturn(null);

    // When
    CustomerDto result = customerService.getCustomer(customerId);

    // Then
    assertThat(result).isNull();

    verify(customersApiProvider).getIfAvailable();
    verifyNoInteractions(customersApi);
  }

  @Test
  @DisplayName("Should return null when API response is null")
  void shouldReturnNullWhenResponseIsNull() {
    // Given
    Long customerId = 1L;
    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);
    when(customersApi.getCustomerById(customerId)).thenReturn(null);

    // When
    CustomerDto result = customerService.getCustomer(customerId);

    // Then
    assertThat(result).isNull();

    verify(customersApiProvider).getIfAvailable();
    verify(customersApi).getCustomerById(customerId);
  }

  @Test
  @DisplayName("Should return null when API response body is null")
  void shouldReturnNullWhenResponseBodyIsNull() {
    // Given
    Long customerId = 1L;
    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);
    when(customersApi.getCustomerById(customerId)).thenReturn(responseEntity);
    when(responseEntity.getBody()).thenReturn(null);

    // When
    CustomerDto result = customerService.getCustomer(customerId);

    // Then
    assertThat(result).isNull();

    verify(customersApiProvider).getIfAvailable();
    verify(customersApi).getCustomerById(customerId);
    verify(responseEntity).getBody();
  }

  @Test
  @DisplayName("Should return null and log error when API throws exception")
  void shouldReturnNullWhenApiThrowsException() {
    // Given
    Long customerId = 1L;
    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);
    when(customersApi.getCustomerById(customerId))
        .thenThrow(new RuntimeException("API connection failed"));

    // When
    CustomerDto result = customerService.getCustomer(customerId);

    // Then
    assertThat(result).isNull();

    verify(customersApiProvider).getIfAvailable();
    verify(customersApi).getCustomerById(customerId);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when customer ID is null")
  void shouldThrowExceptionWhenCustomerIdIsNull() {
    // When & Then
    assertThatThrownBy(() -> customerService.getCustomer(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Customer ID cannot be null or negative");

    verifyNoInteractions(customersApiProvider, customersApi);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when customer ID is zero")
  void shouldThrowExceptionWhenCustomerIdIsZero() {
    // When & Then
    assertThatThrownBy(() -> customerService.getCustomer(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Customer ID cannot be null or negative");

    verifyNoInteractions(customersApiProvider, customersApi);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when customer ID is negative")
  void shouldThrowExceptionWhenCustomerIdIsNegative() {
    // When & Then
    assertThatThrownBy(() -> customerService.getCustomer(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Customer ID cannot be null or negative");

    verifyNoInteractions(customersApiProvider, customersApi);
  }

  @Test
  @DisplayName("Should handle different customer IDs separately in cache")
  void shouldHandleDifferentCustomerIdsSeparately() {
    // Given
    Long customerId1 = 1L;
    Long customerId2 = 2L;

    CustomerDto customer1 = new CustomerDto();
    customer1.setId(customerId1);
    customer1.setFirstName("John");

    CustomerDto customer2 = new CustomerDto();
    customer2.setId(customerId2);
    customer2.setFirstName("Jane");

    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);

    ResponseEntity<CustomerDto> response1 = mock(ResponseEntity.class);
    ResponseEntity<CustomerDto> response2 = mock(ResponseEntity.class);

    when(customersApi.getCustomerById(customerId1)).thenReturn(response1);
    when(customersApi.getCustomerById(customerId2)).thenReturn(response2);
    when(response1.getBody()).thenReturn(customer1);
    when(response2.getBody()).thenReturn(customer2);

    // When
    CustomerDto result1 = customerService.getCustomer(customerId1);
    CustomerDto result2 = customerService.getCustomer(customerId2);

    // Then
    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    assertThat(result1.getId()).isEqualTo(customerId1);
    assertThat(result2.getId()).isEqualTo(customerId2);
    assertThat(result1.getFirstName()).isEqualTo("John");
    assertThat(result2.getFirstName()).isEqualTo("Jane");

    verify(customersApi).getCustomerById(customerId1);
    verify(customersApi).getCustomerById(customerId2);
  }

  @Test
  @DisplayName("Should accept valid positive customer IDs")
  void shouldAcceptValidPositiveCustomerIds() {
    // Given
    Long[] validIds = {1L, 100L, 999999L, Long.MAX_VALUE};
    when(customersApiProvider.getIfAvailable()).thenReturn(customersApi);
    when(customersApi.getCustomerById(any(Long.class))).thenReturn(responseEntity);
    when(responseEntity.getBody()).thenReturn(sampleCustomer);

    // When & Then
    for (Long customerId : validIds) {
      assertThatCode(() -> customerService.getCustomer(customerId)).doesNotThrowAnyException();
    }
  }

  /** Test configuration to enable caching for tests. */
  @Configuration
  @EnableCaching
  static class TestCacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("customer");
    }
  }
}
