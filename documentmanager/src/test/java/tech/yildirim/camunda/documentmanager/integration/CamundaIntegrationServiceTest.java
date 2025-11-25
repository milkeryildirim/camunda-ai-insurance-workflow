package tech.yildirim.camunda.documentmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yildirim.camunda.documentmanager.document.DocumentType;
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * Comprehensive test suite for {@link CamundaIntegrationService}.
 * <p>
 * This test class covers all scenarios including:
 * <ul>
 *   <li>Successful adjuster report and customer invoice notifications</li>
 *   <li>Input validation for null/empty parameters</li>
 *   <li>Camunda integration and connection exception handling</li>
 *   <li>Generic document upload routing based on document type</li>
 *   <li>Message correlation key and process variable validation</li>
 *   <li>Error propagation and logging behavior</li>
 * </ul>
 * </p>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CamundaIntegrationService Tests")
class CamundaIntegrationServiceTest {

    private static final String TEST_CLAIM_NUMBER = "CLAIM-2024-001";
    private static final String TEST_ADJUSTER_REPORT_URL = "https://docs.example.com/adjuster/report123.pdf";
    private static final String TEST_CUSTOMER_INVOICE_URL = "https://docs.example.com/customer/invoice456.pdf";

    private static final String ADJUSTER_REPORT_MESSAGE = "adjuster_report_received";
    private static final String CUSTOMER_INVOICE_MESSAGE = "customer_invoice_received";
    private static final String CLAIM_FILE_NUMBER_VAR = "claim_file_number";
    private static final String ADJUSTER_REPORT_URL_VAR = "adjuster_report_url";
    private static final String CUSTOMER_INVOICE_URL_VAR = "customer_invoice_url";

    @Mock
    private CamundaRestClient mockCamundaRestClient;

    private CamundaIntegrationService camundaIntegrationService;

    @BeforeEach
    void setUp() {
        camundaIntegrationService = new CamundaIntegrationService(mockCamundaRestClient);
    }

    @Nested
    @DisplayName("Adjuster Report Notification Tests")
    class AdjusterReportNotificationTests {

        @Test
        @DisplayName("Should successfully notify Camunda about adjuster report received")
        void shouldSuccessfullyNotifyAdjusterReportReceived() {
            // given
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();

            // Verify message structure
            assertThat(capturedMessage.getMessageName()).isEqualTo(ADJUSTER_REPORT_MESSAGE);
            assertThat(capturedMessage.getBusinessKey()).isEqualTo(TEST_CLAIM_NUMBER);

            // Verify correlation keys
            assertThat(capturedMessage.getCorrelationKeys())
                .hasSize(1)
                .containsKey(CLAIM_FILE_NUMBER_VAR);
            assertThat(capturedMessage.getCorrelationKeys().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(TEST_CLAIM_NUMBER);
            assertThat(capturedMessage.getCorrelationKeys().get(CLAIM_FILE_NUMBER_VAR).getType())
                .isEqualTo("String");

            // Verify process variables
            assertThat(capturedMessage.getProcessVariables())
                .hasSize(2)
                .containsKeys(CLAIM_FILE_NUMBER_VAR, ADJUSTER_REPORT_URL_VAR);
            assertThat(capturedMessage.getProcessVariables().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(TEST_CLAIM_NUMBER);
            assertThat(capturedMessage.getProcessVariables().get(ADJUSTER_REPORT_URL_VAR).getValue())
                .isEqualTo(TEST_ADJUSTER_REPORT_URL);
        }

        @Test
        @DisplayName("Should propagate CamundaIntegrationException from REST client")
        void shouldPropagateCamundaIntegrationException() {
            // given
            CamundaIntegrationException originalException =
                new CamundaIntegrationException("Process definition not found");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL))
                .isInstanceOf(CamundaIntegrationException.class)
                .isEqualTo(originalException);
        }

        @Test
        @DisplayName("Should propagate CamundaConnectionException from REST client")
        void shouldPropagateCamundaConnectionException() {
            // given
            CamundaConnectionException originalException =
                new CamundaConnectionException("Camunda Engine is unreachable");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL))
                .isInstanceOf(CamundaConnectionException.class)
                .isEqualTo(originalException);
        }

        @Test
        @DisplayName("Should wrap unexpected exceptions in CamundaIntegrationException")
        void shouldWrapUnexpectedExceptions() {
            // given
            RuntimeException originalException = new RuntimeException("Unexpected error");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Unexpected error during adjuster report notification for claim: " + TEST_CLAIM_NUMBER)
                .hasCause(originalException);
        }
    }

    @Nested
    @DisplayName("Customer Invoice Notification Tests")
    class CustomerInvoiceNotificationTests {

        @Test
        @DisplayName("Should successfully notify Camunda about customer invoice received")
        void shouldSuccessfullyNotifyCustomerInvoiceReceived() {
            // given
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, TEST_CUSTOMER_INVOICE_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();

            // Verify message structure
            assertThat(capturedMessage.getMessageName()).isEqualTo(CUSTOMER_INVOICE_MESSAGE);
            assertThat(capturedMessage.getBusinessKey()).isEqualTo(TEST_CLAIM_NUMBER);

            // Verify correlation keys
            assertThat(capturedMessage.getCorrelationKeys())
                .hasSize(1)
                .containsKey(CLAIM_FILE_NUMBER_VAR);
            assertThat(capturedMessage.getCorrelationKeys().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(TEST_CLAIM_NUMBER);
            assertThat(capturedMessage.getCorrelationKeys().get(CLAIM_FILE_NUMBER_VAR).getType())
                .isEqualTo("String");

            // Verify process variables
            assertThat(capturedMessage.getProcessVariables())
                .hasSize(2)
                .containsKeys(CLAIM_FILE_NUMBER_VAR, CUSTOMER_INVOICE_URL_VAR);
            assertThat(capturedMessage.getProcessVariables().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(TEST_CLAIM_NUMBER);
            assertThat(capturedMessage.getProcessVariables().get(CUSTOMER_INVOICE_URL_VAR).getValue())
                .isEqualTo(TEST_CUSTOMER_INVOICE_URL);
        }

        @Test
        @DisplayName("Should propagate CamundaIntegrationException from REST client")
        void shouldPropagateCamundaIntegrationException() {
            // given
            CamundaIntegrationException originalException =
                new CamundaIntegrationException("Message correlation failed");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, TEST_CUSTOMER_INVOICE_URL))
                .isInstanceOf(CamundaIntegrationException.class)
                .isEqualTo(originalException);
        }

        @Test
        @DisplayName("Should wrap unexpected exceptions in CamundaIntegrationException")
        void shouldWrapUnexpectedExceptions() {
            // given
            IllegalStateException originalException = new IllegalStateException("Invalid state");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, TEST_CUSTOMER_INVOICE_URL))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Unexpected error during customer invoice notification for claim: " + TEST_CLAIM_NUMBER)
                .hasCause(originalException);
        }
    }

    @Nested
    @DisplayName("Generic Document Upload Notification Tests")
    class GenericDocumentUploadNotificationTests {

        @Test
        @DisplayName("Should route ADJUSTER_REPORT to adjuster report notification")
        void shouldRouteAdjusterReportToAdjusterReportNotification() {
            // given
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyDocumentUploaded(
                TEST_CLAIM_NUMBER, DocumentType.ADJUSTER_REPORT, TEST_ADJUSTER_REPORT_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageName()).isEqualTo(ADJUSTER_REPORT_MESSAGE);
            assertThat(capturedMessage.getProcessVariables().get(ADJUSTER_REPORT_URL_VAR).getValue())
                .isEqualTo(TEST_ADJUSTER_REPORT_URL);
        }

        @Test
        @DisplayName("Should route INVOICE to customer invoice notification")
        void shouldRouteInvoiceToCustomerInvoiceNotification() {
            // given
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyDocumentUploaded(
                TEST_CLAIM_NUMBER, DocumentType.INVOICE, TEST_CUSTOMER_INVOICE_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageName()).isEqualTo(CUSTOMER_INVOICE_MESSAGE);
            assertThat(capturedMessage.getProcessVariables().get(CUSTOMER_INVOICE_URL_VAR).getValue())
                .isEqualTo(TEST_CUSTOMER_INVOICE_URL);
        }

        @Test
        @DisplayName("Should handle null document type with NullPointerException")
        void shouldHandleNullDocumentTypeWithNullPointerException() {
            // when & then - NPE is expected behavior for switch statement
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyDocumentUploaded(TEST_CLAIM_NUMBER, null, TEST_CUSTOMER_INVOICE_URL))
                .isInstanceOf(NullPointerException.class);

            verifyNoMoreInteractions(mockCamundaRestClient);
        }

        @Test
        @DisplayName("Should propagate exceptions from specific notification methods")
        void shouldPropagateExceptionsFromSpecificNotificationMethods() {
            // given
            CamundaConnectionException originalException =
                new CamundaConnectionException("Connection timeout");
            doThrow(originalException).when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when & then
            assertThatThrownBy(() ->
                camundaIntegrationService.notifyDocumentUploaded(
                    TEST_CLAIM_NUMBER, DocumentType.ADJUSTER_REPORT, TEST_ADJUSTER_REPORT_URL))
                .isInstanceOf(CamundaConnectionException.class)
                .isEqualTo(originalException);
        }
    }

    @Nested
    @DisplayName("Message Content Validation Tests")
    class MessageContentValidationTests {

        @Test
        @DisplayName("Should create message with correct business key for adjuster report")
        void shouldCreateMessageWithCorrectBusinessKeyForAdjusterReport() {
            // given
            String claimNumber = "SPECIAL-CLAIM-999";
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyAdjusterReportReceived(claimNumber, TEST_ADJUSTER_REPORT_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getBusinessKey()).isEqualTo(claimNumber);
            assertThat(capturedMessage.getCorrelationKeys().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(claimNumber);
            assertThat(capturedMessage.getProcessVariables().get(CLAIM_FILE_NUMBER_VAR).getValue())
                .isEqualTo(claimNumber);
        }

        @Test
        @DisplayName("Should create message with correct process variables for customer invoice")
        void shouldCreateMessageWithCorrectProcessVariablesForCustomerInvoice() {
            // given
            String invoiceUrl = "https://secure.documents.com/invoice/12345.pdf";
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, invoiceUrl);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getProcessVariables().get(CUSTOMER_INVOICE_URL_VAR).getValue())
                .isEqualTo(invoiceUrl);
            assertThat(capturedMessage.getProcessVariables().get(CUSTOMER_INVOICE_URL_VAR).getType())
                .isEqualTo("String");
        }

        @Test
        @DisplayName("Should create message with correct variable types")
        void shouldCreateMessageWithCorrectVariableTypes() {
            // given
            doNothing().when(mockCamundaRestClient).sendMessage(any(CamundaMessageDTO.class));

            // when
            camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL);

            // then
            ArgumentCaptor<CamundaMessageDTO> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDTO.class);
            verify(mockCamundaRestClient).sendMessage(messageCaptor.capture());

            CamundaMessageDTO capturedMessage = messageCaptor.getValue();

            // All variables should be String type
            capturedMessage.getCorrelationKeys().values().forEach(value ->
                assertThat(value.getType()).isEqualTo("String"));
            capturedMessage.getProcessVariables().values().forEach(value ->
                assertThat(value.getType()).isEqualTo("String"));
        }
    }
}
