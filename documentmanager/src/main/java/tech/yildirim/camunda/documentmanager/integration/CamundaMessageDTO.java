package tech.yildirim.camunda.documentmanager.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for triggering Camunda message events via REST API.
 * <p>
 * This DTO is used to send message events to a Camunda BPM engine, allowing
 * external applications to trigger intermediate message events in running
 * process instances or start new processes with message start events.
 * </p>
 * <p>
 * Example usage for triggering a document uploaded event:
 * <pre>
 * CamundaMessageDTO message = CamundaMessageDTO.builder()
 *     .messageName("DocumentUploaded")
 *     .businessKey("CLAIM-123456")
 *     .addCorrelationKey("claimNumber", "CLAIM-123456")
 *     .addProcessVariable("documentId", 42L)
 *     .addProcessVariable("documentType", "INVOICE")
 *     .build();
 * </pre>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CamundaMessageDTO {

  /**
   * The name of the message event to trigger in Camunda.
   * This must match the message name defined in the BPMN model.
   * <p>
   * Example: "DocumentUploaded", "ClaimSubmitted", "AdjusterAssigned"
   * </p>
   */
  @NotBlank(message = "Message name cannot be blank")
  private String messageName;

  /**
   * Optional business key to identify the process instance.
   * Used for correlation when triggering intermediate message events
   * or as a business identifier for new process instances.
   * <p>
   * Example: "CLAIM-123456", "POLICY-789012"
   * </p>
   */
  private String businessKey;

  /**
   * Correlation keys used to match message events to specific process instances.
   * These are used by Camunda to identify which process instance(s) should
   * receive the message event.
   * <p>
   * Key-value pairs where the key is the correlation key name defined in BPMN
   * and the value contains both the actual value and its type.
   * </p>
   */
  @Builder.Default
  private Map<String, VariableValue> correlationKeys = new HashMap<>();

  /**
   * Process variables to set when the message is triggered.
   * These variables will be available in the process instance
   * after the message event is received.
   * <p>
   * Key-value pairs where the key is the variable name and
   * the value contains both the actual value and its type.
   * </p>
   */
  @Builder.Default
  private Map<String, VariableValue> processVariables = new HashMap<>();

  /**
   * Adds a correlation key to the message.
   * Convenience method for building correlation keys fluently.
   *
   * @param key the correlation key name as defined in BPMN
   * @param value the correlation value to match against
   * @return this CamundaMessageDTO for method chaining
   */
  public CamundaMessageDTO addCorrelationKey(String key, Object value) {
    this.correlationKeys.put(key, VariableValue.of(value));
    return this;
  }

  /**
   * Adds a process variable to the message.
   * Convenience method for building process variables fluently.
   *
   * @param key the variable name
   * @param value the variable value
   * @return this CamundaMessageDTO for method chaining
   */
  public CamundaMessageDTO addProcessVariable(String key, Object value) {
    this.processVariables.put(key, VariableValue.of(value));
    return this;
  }

  /**
   * Represents a typed variable value for Camunda process engine.
   * <p>
   * Camunda requires variables to have both a value and a type specification.
   * This class encapsulates both the actual value and its corresponding
   * Camunda variable type.
   * </p>
   *
   * @author M. Ilker Yildirim
   * @version 1.0
   * @since 1.0
   */
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class VariableValue {

    /**
     * The actual variable value.
     * Can be any serializable object supported by Camunda.
     */
    private Object value;

    /**
     * The Camunda variable type.
     * Common types: "String", "Integer", "Long", "Double", "Boolean", "Date"
     */
    private String type;

    /**
     * Creates a String variable value.
     *
     * @param value the string value
     * @return VariableValue with type "String"
     */
    public static VariableValue string(String value) {
      return new VariableValue(value, "String");
    }

    /**
     * Creates an Integer variable value.
     *
     * @param value the integer value
     * @return VariableValue with type "Integer"
     */
    public static VariableValue integer(Integer value) {
      return new VariableValue(value, "Integer");
    }

    /**
     * Creates a Long variable value.
     *
     * @param value the long value
     * @return VariableValue with type "Long"
     */
    public static VariableValue longVal(Long value) {
      return new VariableValue(value, "Long");
    }

    /**
     * Creates a Double variable value.
     *
     * @param value the double value
     * @return VariableValue with type "Double"
     */
    public static VariableValue doubleVal(Double value) {
      return new VariableValue(value, "Double");
    }

    /**
     * Creates a Boolean variable value.
     *
     * @param value the boolean value
     * @return VariableValue with type "Boolean"
     */
    public static VariableValue bool(Boolean value) {
      return new VariableValue(value, "Boolean");
    }

    /**
     * Creates a variable value with automatic type detection.
     * <p>
     * Automatically determines the Camunda type based on the Java type
     * of the provided value.
     * </p>
     *
     * @param value the value to wrap
     * @return VariableValue with automatically determined type
     * @throws IllegalArgumentException if the type cannot be determined
     */
    public static VariableValue of(Object value) {
      if (value == null) {
        return new VariableValue(null, "Object");
      }

      return switch (value) {
        case String s -> string(s);
        case Integer i -> integer(i);
        case Long l -> longVal(l);
        case Double d -> doubleVal(d);
        case Boolean b -> bool(b);
        default -> new VariableValue(value, "Object");
      };
    }
  }
}
