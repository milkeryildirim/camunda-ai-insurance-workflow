package tech.yildirim.camunda.insurance.workers.claim;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request record for claim rejection due to invalid policy.
 *
 * <p>This record encapsulates all the required data for processing a claim rejection and uses Bean
 * Validation annotations to ensure data integrity. It serves as a data transfer object between the
 * Camunda external task and the business logic.
 *
 * <p>All fields are validated using standard JSR-303 annotations:
 *
 * <ul>
 *   <li>{@code @NotNull} - ensures the field is not null
 *   <li>{@code @NotBlank} - ensures string fields are not null, empty, or whitespace-only
 *   <li>{@code @Positive} - ensures numeric fields are positive
 *   <li>{@code @Email} - ensures email fields have valid email format
 * </ul>
 *
 * @param claimId the unique identifier of the claim being rejected
 * @param claimFileNumber the file reference number for the claim
 * @param customerFirstName the first name of the customer who filed the claim
 * @param customerLastName the last name of the customer who filed the claim
 * @param customerNotificationEmail the email address to send notifications to
 * @param policyNumber the policy number associated with the claim
 * @param claimType the type of claim (AUTO, HEALTH, HOME)
 * @author Yildirim
 * @since 1.0
 */
public record ClaimRejectionRequest(
    @NotNull(message = "Claim ID cannot be null") @Positive(message = "Claim ID must be positive")
        Long claimId,
    @NotBlank(message = "Claim file number cannot be blank") String claimFileNumber,
    @NotBlank(message = "Customer first name cannot be blank") String customerFirstName,
    @NotBlank(message = "Customer last name cannot be blank") String customerLastName,
    @NotBlank(message = "Customer notification email cannot be blank")
        @Email(message = "Customer notification email must be a valid email address")
        String customerNotificationEmail,
    @NotBlank(message = "Policy number cannot be blank") String policyNumber,
    @NotBlank(message = "Claim type cannot be blank") String claimType) {}
