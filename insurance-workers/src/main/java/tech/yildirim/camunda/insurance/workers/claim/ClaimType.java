package tech.yildirim.camunda.insurance.workers.claim;

/**
 * Enumeration representing the different types of insurance claims. This enum defines the supported
 * claim categories in the insurance workflow system.
 */
public enum ClaimType {

  /**
   * Automobile insurance claims. Covers vehicle-related incidents such as accidents, theft, or
   * damage.
   */
  AUTO,

  /**
   * Home insurance claims. Covers property-related incidents such as fire, theft, or natural
   * disasters.
   */
  HOME,

  /** Health insurance claims. Covers medical expenses and healthcare-related costs. */
  HEALTH
}
