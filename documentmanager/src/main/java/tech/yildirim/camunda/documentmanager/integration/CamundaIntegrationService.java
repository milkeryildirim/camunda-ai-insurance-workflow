package tech.yildirim.camunda.documentmanager.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tech.yildirim.camunda.documentmanager.document.DocumentType;
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * Service for integrating document management operations with Camunda BPM workflows.
 *
 * <p>This service provides high-level business operations for notifying Camunda about document
 * upload events in the insurance claim processing workflow. It handles the mapping between document
 * management events and appropriate Camunda message correlations.
 *
 * <p>The service manages two main document upload scenarios:
 *
 * <ul>
 *   <li><strong>Adjuster Report Upload:</strong> Notifies when an insurance adjuster uploads a
 *       damage assessment report for a claim
 *   <li><strong>Customer Invoice Upload:</strong> Notifies when a customer uploads an invoice for
 *       claim reimbursement
 * </ul>
 *
 * <p>Example usage in document upload controller:
 *
 * <pre>{@code
 * @Autowired
 * private CamundaIntegrationService camundaService;
 *
 * public void handleDocumentUpload(String claimNumber, DocumentType type, String documentUrl) {
 *     switch (type) {
 *         case ADJUSTER_REPORT ->
 *             camundaService.notifyAdjusterReportReceived(claimNumber, documentUrl);
 *         case INVOICE ->
 *             camundaService.notifyCustomerInvoiceReceived(claimNumber, documentUrl);
 *     }
 * }
 * }</pre>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 * @see CamundaRestClient
 * @see DocumentType
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class CamundaIntegrationService {

  /** Camunda REST client for sending message correlation requests */
  private final CamundaRestClient camundaRestClient;

  /** Message name for adjuster report received events */
  private static final String ADJUSTER_REPORT_MESSAGE = "adjuster_report_received";

  /** Message name for customer invoice received events */
  private static final String CUSTOMER_INVOICE_MESSAGE = "customer_invoice_received";

  /** Process variable name for claim file number */
  private static final String CLAIM_FILE_NUMBER_VAR = "claim_file_number";

  /** Process variable name for adjuster report URL */
  private static final String ADJUSTER_REPORT_URL_VAR = "adjuster_report_url";

  /** Process variable name for customer invoice URL */
  private static final String CUSTOMER_INVOICE_URL_VAR = "customer_invoice_url";

  /**
   * Notifies Camunda workflow that an adjuster report has been received.
   *
   * <p>This method triggers the "adjuster_report_received" message event in Camunda, which
   * typically continues the claim processing workflow after the insurance adjuster has completed
   * their damage assessment and uploaded the report.
   *
   * <p>The notification includes:
   *
   * <ul>
   *   <li>Business key: claim number for process instance identification
   *   <li>Correlation key: claim number for message correlation
   *   <li>Process variables: claim number and adjuster report URL
   * </ul>
   *
   * <p>Typical workflow sequence:
   *
   * <ol>
   *   <li>Claim is submitted and process instance starts
   *   <li>Adjuster is assigned to the claim
   *   <li>Adjuster uploads damage assessment report
   *   <li><strong>This method is called</strong> to notify workflow
   *   <li>Workflow continues with report review and decision making
   * </ol>
   *
   * @param claimNumber the insurance claim number (e.g., "CLAIM-2024-001")
   * @param adjusterReportUrl the URL where the adjuster report can be accessed
   * @throws IllegalArgumentException if claimNumber or adjusterReportUrl is null/empty
   * @throws CamundaIntegrationException if there's a business logic error in Camunda
   * @throws CamundaConnectionException if Camunda Engine is unreachable
   */
  public void notifyAdjusterReportReceived(
      @NotBlank(message = "Claim number cannot be blank") String claimNumber,
      @NotBlank(message = "Adjuster report URL cannot be blank") String adjusterReportUrl) {

    log.info("Notifying Camunda about adjuster report received for claim: {}", claimNumber);
    log.debug("Adjuster report URL: {}", adjusterReportUrl);

    try {
      CamundaMessageDTO message =
          CamundaMessageDTO.builder()
              .messageName(ADJUSTER_REPORT_MESSAGE)
              .businessKey(claimNumber)
              .build();

      // Add correlation key for message correlation
      message.addCorrelationKey(CLAIM_FILE_NUMBER_VAR, claimNumber);

      // Add process variables
      message.addProcessVariable(CLAIM_FILE_NUMBER_VAR, claimNumber);
      message.addProcessVariable(ADJUSTER_REPORT_URL_VAR, adjusterReportUrl);

      camundaRestClient.sendMessage(message);

      log.info("Successfully notified Camunda about adjuster report for claim: {}", claimNumber);

    } catch (CamundaIntegrationException | CamundaConnectionException e) {
      log.error(
          "Failed to notify Camunda about adjuster report for claim: {} - {}",
          claimNumber,
          e.getMessage());
      throw e; // Re-throw to allow caller to handle appropriately
    } catch (Exception e) {
      log.error(
          "Unexpected error notifying Camunda about adjuster report for claim: {}", claimNumber, e);
      throw new CamundaIntegrationException(
          "Unexpected error during adjuster report notification for claim: " + claimNumber, e);
    }
  }

  /**
   * Notifies Camunda workflow that a customer invoice has been received.
   *
   * <p>This method triggers the "customer_invoice_received" message event in Camunda, which
   * typically starts or continues the claim reimbursement workflow after the customer has uploaded
   * their invoice/receipt for expenses.
   *
   * <p>The notification includes:
   *
   * <ul>
   *   <li>Business key: claim number for process instance identification
   *   <li>Correlation key: claim number for message correlation
   *   <li>Process variables: claim number and customer invoice URL
   * </ul>
   *
   * <p>Typical workflow scenarios:
   *
   * <ul>
   *   <li><strong>New Claim:</strong> Customer uploads invoice when submitting initial claim
   *   <li><strong>Additional Invoice:</strong> Customer uploads supplementary invoices
   *   <li><strong>Repair Invoice:</strong> Customer uploads receipts after approved repairs
   * </ul>
   *
   * @param claimNumber the insurance claim number (e.g., "CLAIM-2024-001")
   * @param customerInvoiceUrl the URL where the customer invoice can be accessed
   * @throws IllegalArgumentException if claimNumber or customerInvoiceUrl is null/empty
   * @throws CamundaIntegrationException if there's a business logic error in Camunda
   * @throws CamundaConnectionException if Camunda Engine is unreachable
   */
  public void notifyCustomerInvoiceReceived(
      @NotBlank(message = "Claim number cannot be blank") String claimNumber,
      @NotBlank(message = "Customer invoice URL cannot be blank") String customerInvoiceUrl) {

    log.info("Notifying Camunda about customer invoice received for claim: {}", claimNumber);
    log.debug("Customer invoice URL: {}", customerInvoiceUrl);

    try {
      CamundaMessageDTO message =
          CamundaMessageDTO.builder()
              .messageName(CUSTOMER_INVOICE_MESSAGE)
              .businessKey(claimNumber)
              .build();

      // Add correlation key for message correlation
      message.addCorrelationKey(CLAIM_FILE_NUMBER_VAR, claimNumber);

      // Add process variables
      message.addProcessVariable(CLAIM_FILE_NUMBER_VAR, claimNumber);
      message.addProcessVariable(CUSTOMER_INVOICE_URL_VAR, customerInvoiceUrl);

      camundaRestClient.sendMessage(message);

      log.info("Successfully notified Camunda about customer invoice for claim: {}", claimNumber);

    } catch (CamundaIntegrationException | CamundaConnectionException e) {
      log.error(
          "Failed to notify Camunda about customer invoice for claim: {} - {}",
          claimNumber,
          e.getMessage());
      throw e; // Re-throw to allow caller to handle appropriately
    } catch (Exception e) {
      log.error(
          "Unexpected error notifying Camunda about customer invoice for claim: {}",
          claimNumber,
          e);
      throw new CamundaIntegrationException(
          "Unexpected error during customer invoice notification for claim: " + claimNumber, e);
    }
  }

  /**
   * Generic method to notify Camunda about document upload based on document type.
   *
   * <p>This convenience method automatically routes to the appropriate notification method based on
   * the document type. It provides a unified interface for document upload notifications without
   * requiring the caller to know which specific method to use.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * // In DocumentService after successful upload
   * camundaIntegrationService.notifyDocumentUploaded(
   *     claimNumber,
   *     DocumentType.ADJUSTER_REPORT,
   *     downloadUrl
   * );
   * }</pre>
   *
   * @param claimNumber the insurance claim number
   * @param documentType the type of document uploaded
   * @param documentUrl the URL where the document can be accessed
   * @throws IllegalArgumentException if any parameter is null/empty or documentType is unsupported
   * @throws CamundaIntegrationException if there's a business logic error in Camunda
   * @throws CamundaConnectionException if Camunda Engine is unreachable
   */
  public void notifyDocumentUploaded(
      @NotBlank(message = "Claim number cannot be blank") String claimNumber,
      @NotNull(message = "Document type cannot be null") DocumentType documentType,
      @NotBlank(message = "Document URL cannot be blank") String documentUrl) {

    log.info(
        "Processing document upload notification for claim: {}, type: {}",
        claimNumber,
        documentType);

    switch (documentType) {
      case ADJUSTER_REPORT -> notifyAdjusterReportReceived(claimNumber, documentUrl);
      case INVOICE -> notifyCustomerInvoiceReceived(claimNumber, documentUrl);
      default -> {
        String errorMsg = "Unsupported document type for Camunda notification: " + documentType;
        log.error(errorMsg + " for claim: {}", claimNumber);
        throw new IllegalArgumentException(errorMsg);
      }
    }
  }
}
