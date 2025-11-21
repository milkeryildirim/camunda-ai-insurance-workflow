package tech.yildirim.camunda.insurance.workers.payment;

import org.springframework.stereotype.Component;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;

/**
 * Camunda external task worker responsible for calculating partial payment amounts.
 *
 * <p>This worker calculates payment amounts at 80% of the original invoice amount, typically used
 * for payments from non-partnered providers where a deduction is applied. The calculation follows
 * the formula: Approved Amount = Invoice Amount × 80%.
 *
 * <p>The worker subscribes to the topic "insurance.payment.calculate-partial" and processes
 * payment calculations for scenarios where only partial payment is authorized.
 *
 * <p>Process flow:
 *
 * <ul>
 *   <li>Extracts invoice amount from process variables
 *   <li>Applies 80% calculation (20% deduction)
 *   <li>Sets the calculated amount as approved amount
 *   <li>Updates the claim decision with approved amount and additional notes
 * </ul>
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Component
public class PartialPaymentCalculationWorker extends AbstractPaymentCalculationWorker {

  private static final String TOPIC_NAME = "insurance.payment.calculate-partial";
  private static final int PAYMENT_PERCENTAGE = 80;

  /**
   * Constructs a PartialPaymentCalculationWorker with the required ClaimService dependency.
   *
   * @param claimService the service for managing claim operations
   */
  public PartialPaymentCalculationWorker(ClaimService claimService) {
    super(claimService);
  }

  /**
   * Returns the payment percentage for partial payments.
   *
   * @return 80 (representing 80% of the invoice amount)
   */
  @Override
  protected int getPaymentPercentage() {
    return PAYMENT_PERCENTAGE;
  }

  /**
   * Returns the topic name that this worker subscribes to.
   *
   * @return the topic name for partial payment calculation tasks
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
