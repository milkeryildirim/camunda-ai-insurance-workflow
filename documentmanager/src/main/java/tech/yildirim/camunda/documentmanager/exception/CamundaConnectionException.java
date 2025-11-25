package tech.yildirim.camunda.documentmanager.exception;

/**
 * Exception thrown when Camunda BPM Engine is unreachable or connection fails.
 * <p>
 * This exception indicates infrastructure-level connectivity issues
 * such as network timeouts, connection refused, DNS resolution failures,
 * or Camunda Engine being down.
 * </p>
 * <p>
 * Common scenarios that trigger this exception:
 * <ul>
 *   <li>Network connection timeouts</li>
 *   <li>Camunda Engine server is down or unreachable</li>
 *   <li>DNS resolution failures for Camunda host</li>
 *   <li>Firewall blocking access to Camunda ports</li>
 *   <li>SSL/TLS certificate issues</li>
 *   <li>HTTP proxy configuration problems</li>
 * </ul>
 * </p>
 * <p>
 * This exception typically indicates temporary issues that might resolve
 * with retry logic or infrastructure fixes. It should be distinguished
 * from business logic errors represented by CamundaIntegrationException.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * try {
 *     camundaClient.sendMessage(messageDto);
 * } catch (CamundaConnectionException e) {
 *     log.error("Camunda Engine is unreachable: {}", e.getMessage());
 *     // Implement retry logic or circuit breaker pattern
 *     notificationService.alertOperations("Camunda connectivity issue");
 * }
 * }
 * </pre>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 * @see tech.yildirim.camunda.documentmanager.integration.CamundaRestClient
 */
public class CamundaConnectionException extends RuntimeException {

    /**
     * Constructs a new CamundaConnectionException with the specified detail message.
     * <p>
     * This constructor is typically used when the connection failure is detected
     * through timeout or other direct connection validation.
     * </p>
     *
     * @param message the detail message explaining the connection failure
     */
    public CamundaConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new CamundaConnectionException with the specified detail message and cause.
     * <p>
     * This constructor is used when wrapping underlying exceptions from HTTP client
     * connection operations, such as ResourceAccessException or ConnectException.
     * </p>
     *
     * @param message the detail message explaining the connection failure
     * @param cause the underlying cause of this connection exception
     */
    public CamundaConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
