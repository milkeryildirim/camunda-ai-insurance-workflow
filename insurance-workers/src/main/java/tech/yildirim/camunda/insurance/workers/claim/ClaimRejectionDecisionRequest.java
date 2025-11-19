package tech.yildirim.camunda.insurance.workers.claim;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request record for claim rejection based on adjuster's decision.
 *
 * <p>This record extends the basic claim rejection request to include additional fields specific to
 * decision-based rejections, such as decision notes and adjuster information. It uses Bean
 * Validation annotations to ensure data integrity.
 *
 * <p>All fields are validated using standard JSR-303 annotations for data integrity and proper
 * error handling.
 *
 * @param claimId the unique identifier of the claim being rejected
 * @param claimFileNumber the file reference number for the claim
 * @param customerFirstName the first name of the customer who filed the claim
 * @param customerLastName the last name of the customer who filed the claim
 * @param customerNotificationEmail the email address to send notifications to
 * @param policyNumber the policy number associated with the claim
 * @param claimType the type of claim (AUTO, HEALTH, HOME)
 * @param decisionNotes the adjuster's notes explaining the rejection decision
 * @param adjusterId the unique identifier of the adjuster who made the decision
 * @author Yildirim
 * @since 1.0
 */
public record ClaimRejectionDecisionRequest(
    @NotNull(message = "Claim ID cannot be null") @Positive(message = "Claim ID must be positive")
        Long claimId,
    @NotBlank(message = "Claim file number cannot be blank") String claimFileNumber,
    @NotBlank(message = "Customer first name cannot be blank") String customerFirstName,
    @NotBlank(message = "Customer last name cannot be blank") String customerLastName,
    @NotBlank(message = "Customer notification email cannot be blank")
        @Email(message = "Customer notification email must be a valid email address")
        String customerNotificationEmail,
    @NotBlank(message = "Policy number cannot be blank") String policyNumber,
    @NotBlank(message = "Claim type cannot be blank") String claimType,
    @NotBlank(message = "Decision notes cannot be blank") String decisionNotes,
    @NotNull(message = "Adjuster ID cannot be null")
        @Positive(message = "Adjuster ID must be positive")
        Long adjusterId) {

  /**
   * Converts this decision request to a basic claim rejection request.
   *
   * @return a ClaimRejectionRequest with the common fields
   */
  public ClaimRejectionRequest toBasicRequest() {
    return new ClaimRejectionRequest(
        claimId,
        claimFileNumber,
        customerFirstName,
        customerLastName,
        customerNotificationEmail,
        policyNumber,
        claimType);
  }
}
