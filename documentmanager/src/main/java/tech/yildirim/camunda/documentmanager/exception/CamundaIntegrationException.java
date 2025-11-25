package tech.yildirim.camunda.documentmanager.exception;

/**
 * Exception thrown when there are integration issues with Camunda BPM Engine.
 * <p>
 * This exception indicates business logic errors or API-level issues
 * such as invalid message formats, correlation failures, or process
 * definition problems.
 * </p>
 * <p>
 * Common scenarios that trigger this exception:
 * <ul>
 *   <li>Invalid message format sent to Camunda</li>
 *   <li>Message correlation failures due to incorrect correlation keys</li>
 *   <li>Process definition not found for message start events</li>
 *   <li>Business rule violations in process execution</li>
 *   <li>Invalid process variables or data types</li>
 * </ul>
 * </p>
 * <p>
 * This exception should be caught and handled gracefully by the calling code
 * to provide meaningful error messages to users and proper error logging.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * try {
 *     camundaClient.sendMessage(messageDto);
 * } catch (CamundaIntegrationException e) {
 *     log.error("Failed to correlate message to Camunda: {}", e.getMessage());
 *     // Handle business logic error appropriately
 * }
 * }
 * </pre>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 * @see tech.yildirim.camunda.documentmanager.integration.CamundaRestClient
 */
public class CamundaIntegrationException extends RuntimeException {

    /**
     * Constructs a new CamundaIntegrationException with the specified detail message.
     * <p>
     * This constructor is typically used when the exception is triggered by
     * business logic validation or API response errors without an underlying cause.
     * </p>
     *
     * @param message the detail message explaining the integration failure
     */
    public CamundaIntegrationException(String message) {
        super(message);
    }

    /**
     * Constructs a new CamundaIntegrationException with the specified detail message and cause.
     * <p>
     * This constructor is used when wrapping underlying exceptions from HTTP client
     * operations or other integration-related errors.
     * </p>
     *
     * @param message the detail message explaining the integration failure
     * @param cause the underlying cause of this exception
     */
    public CamundaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
