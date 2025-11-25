package tech.yildirim.camunda.documentmanager.common;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for HTTP client beans used throughout the application.
 * <p>
 * This configuration provides pre-configured {@link RestTemplate} instances
 * optimized for making HTTP requests to external services, particularly
 * Camunda BPM Engine and other insurance-related microservices.
 * </p>
 * <p>
 * The configuration includes:
 * <ul>
 *   <li>Connection and read timeout settings using modern Spring Boot approach</li>
 *   <li>Request/response logging capabilities through buffering</li>
 *   <li>Error handling configuration</li>
 *   <li>Separate configurations for standard and Camunda-specific operations</li>
 * </ul>
 * </p>
 * <p>
 * This configuration uses manual factory creation approach to ensure
 * compatibility across different Spring Boot versions.
 * </p>
 *
 * @author M. Ilker Yildirim
 * @version 2.0
 * @since 1.0
 * @see RestTemplate
 * @see RestTemplateBuilder
 * @see ClientHttpRequestFactory
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

  /** Default connection timeout duration */
  private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);

  /** Default read timeout duration */
  private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

  /** Extended connection timeout for Camunda operations */
  private static final Duration CAMUNDA_CONNECTION_TIMEOUT = Duration.ofSeconds(45);

  /** Extended read timeout for Camunda operations */
  private static final Duration CAMUNDA_READ_TIMEOUT = Duration.ofSeconds(120);

  /**
   * Creates a configured {@link RestTemplate} bean for general HTTP communication.
   * <p>
   * This RestTemplate is configured with:
   * <ul>
   *   <li>Connection timeout: 30 seconds</li>
   *   <li>Read timeout: 60 seconds</li>
   *   <li>Standard request factory without buffering for optimal performance</li>
   *   <li>Optimized for standard REST API calls</li>
   * </ul>
   * </p>
   * <p>
   * Example usage for Camunda message triggering:
   * <pre>
   * {@code
   * @Autowired
   * private RestTemplate restTemplate;
   *
   * public void triggerCamundaMessage(CamundaMessageDTO message) {
   *     String camundaUrl = "http://localhost:8080/engine-rest/message";
   *     ResponseEntity<Void> response = restTemplate.postForEntity(
   *         camundaUrl, message, Void.class);
   * }
   * }
   * </pre>
   *
   * @param builder the RestTemplateBuilder provided by Spring Boot
   * @return configured RestTemplate instance for general use
   * @throws IllegalArgumentException if builder is null
   */
  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    log.info("Configuring general RestTemplate with connection timeout: {}, read timeout: {}",
             DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT);

    return builder
        .requestFactory(() -> createRequestFactory(DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT))
        .build();
  }

  /**
   * Alternative RestTemplate bean for Camunda-specific operations with extended timeouts.
   * <p>
   * This bean is specifically optimized for Camunda BPM Engine communication
   * with extended timeouts for long-running process operations.
   * </p>
   * <p>
   * Use this when making requests that might take longer, such as:
   * <ul>
   *   <li>Starting complex process instances</li>
   *   <li>Querying large datasets from Camunda</li>
   *   <li>Executing long-running external tasks</li>
   *   <li>Process instance migrations</li>
   *   <li>Historical data queries</li>
   * </ul>
   * </p>
   * <p>
   * Example usage:
   * <pre>
   * {@code
   * @Autowired
   * @Qualifier("camundaRestTemplate")
   * private RestTemplate camundaRestTemplate;
   *
   * public void startProcessInstance(ProcessStartRequest request) {
   *     String url = camundaBaseUrl + "/process-definition/key/" + processKey + "/start";
   *     ProcessInstanceResponse response = camundaRestTemplate.postForObject(
   *         url, request, ProcessInstanceResponse.class);
   * }
   * }
   * </pre>
   *
   * @param builder the RestTemplateBuilder provided by Spring Boot
   * @return RestTemplate configured for Camunda operations with extended timeouts
   * @throws IllegalArgumentException if builder is null
   */
  @Bean("camundaRestTemplate")
  public RestTemplate camundaRestTemplate(RestTemplateBuilder builder) {
    log.info("Configuring Camunda-specific RestTemplate with connection timeout: {}, read timeout: {}",
             CAMUNDA_CONNECTION_TIMEOUT, CAMUNDA_READ_TIMEOUT);

    return builder
        .requestFactory(() -> createRequestFactory(CAMUNDA_CONNECTION_TIMEOUT, CAMUNDA_READ_TIMEOUT))
        .build();
  }

  /**
   * Creates a buffered RestTemplate for operations requiring request/response logging.
   * <p>
   * This RestTemplate enables buffering of request and response bodies, which allows
   * multiple reads of the stream. This is essential for:
   * <ul>
   *   <li>Request/response logging interceptors</li>
   *   <li>Error handling that needs to read response body</li>
   *   <li>Debugging and monitoring capabilities</li>
   *   <li>Retry mechanisms that need to replay requests</li>
   * </ul>
   * </p>
   * <p>
   * <strong>Performance Note:</strong> Buffering consumes additional memory
   * as the entire request/response body is stored in memory. Use this bean
   * only when buffering is specifically required.
   * </p>
   * <p>
   * Example usage with logging:
   * <pre>
   * {@code
   * @Autowired
   * @Qualifier("bufferedRestTemplate")
   * private RestTemplate bufferedRestTemplate;
   *
   * public void makeTrackedRequest(RequestData data) {
   *     // Request/response will be fully logged due to buffering
   *     ResponseEntity<String> response = bufferedRestTemplate.exchange(
   *         url, HttpMethod.POST, entity, String.class);
   * }
   * }
   * </pre>
   *
   * @param builder the RestTemplateBuilder provided by Spring Boot
   * @return RestTemplate with buffering enabled for logging
   */
  @Bean("bufferedRestTemplate")
  public RestTemplate bufferedRestTemplate(RestTemplateBuilder builder) {
    log.info("Configuring buffered RestTemplate for logging capabilities");

    return builder
        .requestFactory(() -> createBufferedRequestFactory(DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT))
        .build();
  }

  /**
   * Creates a standard client HTTP request factory with the specified timeout settings.
   * <p>
   * This factory provides basic HTTP communication capabilities without buffering,
   * optimized for performance in standard REST operations.
   * </p>
   *
   * @param connectTimeout the connection timeout duration
   * @param readTimeout the read timeout duration
   * @return configured ClientHttpRequestFactory
   * @throws IllegalArgumentException if any timeout is null or negative
   */
  private ClientHttpRequestFactory createRequestFactory(Duration connectTimeout, Duration readTimeout) {
    log.debug("Creating request factory with connect timeout: {}, read timeout: {}",
              connectTimeout, readTimeout);

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeout.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());

    return factory;
  }

  /**
   * Creates a buffered client HTTP request factory with the specified timeout settings.
   * <p>
   * This factory wraps a standard {@link SimpleClientHttpRequestFactory} with
   * {@link BufferingClientHttpRequestFactory} to enable request/response buffering.
   * </p>
   *
   * @param connectTimeout the connection timeout duration
   * @param readTimeout the read timeout duration
   * @return configured ClientHttpRequestFactory with buffering enabled
   * @throws IllegalArgumentException if any timeout is null or negative
   */
  private ClientHttpRequestFactory createBufferedRequestFactory(Duration connectTimeout, Duration readTimeout) {
    log.debug("Creating buffered request factory with connect timeout: {}, read timeout: {}",
              connectTimeout, readTimeout);

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) connectTimeout.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());

    // Wrap with buffering to enable request/response body logging
    return new BufferingClientHttpRequestFactory(factory);
  }
}
