package tech.yildirim.camunda.insurance.workers.payment;

import org.springframework.stereotype.Component;
import tech.yildirim.camunda.insurance.workers.claim.ClaimService;

/**
 * Camunda external task worker responsible for calculating full payment amounts.
 *
 * <p>This worker calculates payment amounts at 100% of the original invoice amount, typically used
 * for payments from partnered providers where no deduction is applied. The calculation follows the
 * formula: Approved Amount = Invoice Amount × 100%.
 *
 * <p>The worker subscribes to the topic "insurance.payment.calculate-full" and processes payment
 * calculations for scenarios where full payment is authorized.
 *
 * <p>Process flow:
 *
 * <ul>
 *   <li>Extracts invoice amount from process variables
 *   <li>Applies 100% calculation (no deduction)
 *   <li>Sets the calculated amount as approved amount
 *   <li>Updates the claim decision with approved amount and additional notes
 * </ul>
 *
 * @author M.Ilker Yildirim
 * @since 1.0.0
 */
@Component
public class FullPaymentCalculationWorker extends AbstractPaymentCalculationWorker {

  private static final String TOPIC_NAME = "insurance.payment.calculate-full";
  private static final int PAYMENT_PERCENTAGE = 100;

  /**
   * Constructs a FullPaymentCalculationWorker with the required ClaimService dependency.
   *
   * @param claimService the service for managing claim operations
   */
  public FullPaymentCalculationWorker(ClaimService claimService) {
    super(claimService);
  }

  /**
   * Returns the payment percentage for full payments.
   *
   * @return 100 (representing 100% of the invoice amount)
   */
  @Override
  protected int getPaymentPercentage() {
    return PAYMENT_PERCENTAGE;
  }

  /**
   * Returns the topic name that this worker subscribes to.
   *
   * @return the topic name for full payment calculation tasks
   */
  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }
}
