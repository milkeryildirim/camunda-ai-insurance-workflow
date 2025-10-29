package tech.yildirim.camunda.insurance.workers.claim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import tech.yildirim.aiinsurance.api.generated.model.AutoClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto.ClaimTypeEnum;
import tech.yildirim.aiinsurance.api.generated.model.ClaimDto.StatusEnum;
import tech.yildirim.aiinsurance.api.generated.model.CustomerDto;
import tech.yildirim.aiinsurance.api.generated.model.HealthClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.HomeClaimDto;
import tech.yildirim.aiinsurance.api.generated.model.PolicyDto;
import tech.yildirim.camunda.insurance.workers.common.AbstractCamundaWorker;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;
import tech.yildirim.camunda.insurance.workers.customer.CustomerService;
import tech.yildirim.camunda.insurance.workers.policy.PolicyService;

/**
 * Camunda external task worker responsible for creating insurance claims based on incident reports.
 *
 * <p>This worker processes claim creation requests by:
 * <ul>
 *   <li>Retrieving policy and customer information</li>
 *   <li>Creating appropriate claim type (AUTO, HOME, or HEALTH)</li>
 *   <li>Setting initial claim status and estimated amount</li>
 *   <li>Completing the task with customer and claim information</li>
 * </ul>
 *
 * <p>The worker subscribes to the topic "insurance.claim.create-claim" and expects the following
 * process variables:
 * <ul>
 *   <li>{@code POLICY_NUMBER} - The policy number for which the claim is being created</li>
 *   <li>{@code INCIDENT_DESCRIPTION} - Description of the incident</li>
 *   <li>{@code CLAIM_TYPE} - Type of claim (AUTO, HOME, or HEALTH)</li>
 *   <li>{@code INCIDENT_DATE} - Date when the incident occurred</li>
 *   <li>{@code INCIDENT_ESTIMATED_LOSS_AMOUNT} - Estimated loss amount</li>
 * </ul>
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClaimCreationWorker extends AbstractCamundaWorker {

  private static final String TOPIC_NAME = "insurance.claim.create-claim";

  private final ClaimService claimService;
  private final PolicyService policyService;
  private final CustomerService customerService;

  /**
   * Executes the business logic for creating insurance claims.
   *
   * <p>This method processes the external task by:
   * <ol>
   *   <li>Extracting required variables from the task</li>
   *   <li>Retrieving policy and customer information</li>
   *   <li>Creating the appropriate claim type based on the claim type variable</li>
   *   <li>Completing the task with customer and claim information</li>
   * </ol>
   *
   * @param externalTask the external task containing process variables
   * @param externalTaskService service for task completion and error handling
   * @throws IllegalStateException if policy or customer is not found, or claim creation fails
   * @throws IllegalArgumentException if an unknown claim type is provided
   */
  @Override
  protected void executeBusinessLogic(
      ExternalTask externalTask, ExternalTaskService externalTaskService) {

    log.info("Starting claim creation for task: {}", externalTask.getId());

    // Extract process variables
    String policyNumber = externalTask.getVariable(ProcInstVars.POLICY_NUMBER);
    String incidentDescription = externalTask.getVariable(ProcInstVars.INCIDENT_DESCRIPTION);
    String claimType = externalTask.getVariable(ProcInstVars.CLAIM_TYPE);
    Date incidentDate = externalTask.getVariable(ProcInstVars.INCIDENT_DATE);
    Double estimatedAmount = externalTask.getVariable(ProcInstVars.INCIDENT_ESTIMATED_LOSS_AMOUNT);

    log.debug("Processing claim creation - Policy: {}, Type: {}, Amount: {}",
        policyNumber, claimType, estimatedAmount);

    // Retrieve policy and customer information
    PolicyDto policyDto = policyService.getPolicyByPolicyNumber(policyNumber);
    if (policyDto == null) {
      throw new IllegalStateException("Policy not found for policy number: " + policyNumber);
    }

    CustomerDto customer = customerService.getCustomer(policyDto.getCustomerId());
    if (customer == null) {
      throw new IllegalStateException("Customer not found for customer ID: " + policyDto.getCustomerId());
    }

    // Convert incident date
    LocalDate incidentLocalDate = incidentDate.toInstant()
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate();

    // Create claim based on type
    ClaimDto createdClaim = createClaimByType(claimType, policyDto, incidentDescription,
        incidentLocalDate, estimatedAmount);

    if (createdClaim == null) {
      throw new IllegalStateException("Failed to create claim of type: " + claimType);
    }

    // Safely get claim number with null check
    String claimNumber = createdClaim.getClaimNumber();
    if (claimNumber == null) {
      log.warn("Created claim has null claim number, using claim ID as fallback");
      claimNumber = "CLAIM-" + createdClaim.getId();
    }

    log.info("Successfully created claim with number: {} for policy: {}",
        claimNumber, policyNumber);

    // Complete task with result variables
    completeTask(externalTask, externalTaskService, Map.of(
        ProcInstVars.CUSTOMER_FIRSTNAME, customer.getFirstName(),
        ProcInstVars.CUSTOMER_LASTNAME, customer.getLastName(),
        ProcInstVars.CLAIM_FILE_NUMBER, claimNumber
    ));
  }

  /**
   * Creates a claim of the specified type with the provided information.
   *
   * @param claimType the type of claim to create (AUTO, HOME, or HEALTH)
   * @param policyDto the policy for which the claim is being created
   * @param incidentDescription description of the incident
   * @param incidentDate date when the incident occurred
   * @param estimatedAmount estimated loss amount
   * @return the created claim DTO
   * @throws IllegalArgumentException if an unknown claim type is provided
   */
  private ClaimDto createClaimByType(String claimType, PolicyDto policyDto,
      String incidentDescription, LocalDate incidentDate, Double estimatedAmount) {

    ClaimDto createdClaim;
    BigDecimal estimatedAmountBigDecimal = BigDecimal.valueOf(estimatedAmount);

    switch (claimType.toUpperCase()) {
      case "AUTO" -> {
        AutoClaimDto autoClaimDto = new AutoClaimDto()
            .policyId(policyDto.getId())
            .description(incidentDescription)
            .dateOfIncident(incidentDate)
            .dateReported(LocalDate.now())
            .estimatedAmount(estimatedAmountBigDecimal)
            .status(StatusEnum.SUBMITTED);
        autoClaimDto.setClaimType(ClaimTypeEnum.AUTO_CLAIM_DTO);

        createdClaim = claimService.createAutoClaim(autoClaimDto);
        log.debug("Created AUTO claim with ID: {}", createdClaim != null ? createdClaim.getId() : "null");
      }
      case "HOME" -> {
        HomeClaimDto homeClaimDto = new HomeClaimDto()
            .policyId(policyDto.getId())
            .description(incidentDescription)
            .dateOfIncident(incidentDate)
            .dateReported(LocalDate.now())
            .estimatedAmount(estimatedAmountBigDecimal)
            .status(StatusEnum.SUBMITTED);
        homeClaimDto.setClaimType(ClaimTypeEnum.HOME_CLAIM_DTO);

        createdClaim = claimService.createHomeClaim(homeClaimDto);
        log.debug("Created HOME claim with ID: {}", createdClaim != null ? createdClaim.getId() : "null");
      }
      case "HEALTH" -> {
        HealthClaimDto healthClaimDto = new HealthClaimDto()
            .policyId(policyDto.getId())
            .description(incidentDescription)
            .dateOfIncident(incidentDate)
            .dateReported(LocalDate.now())
            .estimatedAmount(estimatedAmountBigDecimal)
            .status(StatusEnum.SUBMITTED);
        healthClaimDto.setClaimType(ClaimTypeEnum.HEALTH_CLAIM_DTO);

        createdClaim = claimService.createHealthClaim(healthClaimDto);
        log.debug("Created HEALTH claim with ID: {}", createdClaim != null ? createdClaim.getId() : "null");
      }
      default -> throw new IllegalArgumentException("Unknown claim type: " + claimType +
          ". Supported types are: AUTO, HOME, HEALTH");
    }

    return createdClaim;
  }

  /**
   * Returns the topic name that this worker subscribes to.
   *
   * @return the topic name for claim creation tasks
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
