package tech.yildirim.camunda.documentmanager.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import tech.yildirim.camunda.documentmanager.document.DocumentType;
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * Integration tests for {@link CamundaIntegrationService}.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "camunda.bpm.client.base-url=http://localhost:8080/engine-rest"
})
@DisplayName("CamundaIntegrationService Integration Tests")
class CamundaIntegrationServiceIntegrationTest {

    private static final String CAMUNDA_MESSAGE_ENDPOINT = "http://localhost:8080/engine-rest/message";
    private static final String TEST_CLAIM_NUMBER = "INTEGRATION-CLAIM-001";
    private static final String TEST_ADJUSTER_REPORT_URL = "https://docs.example.com/adjuster-report.pdf";
    private static final String TEST_CUSTOMER_INVOICE_URL = "https://docs.example.com/customer-invoice.pdf";

    @Autowired
    private CamundaIntegrationService camundaIntegrationService;

    @Autowired
    @Qualifier("camundaRestTemplate")
    private RestTemplate camundaRestTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(camundaRestTemplate);
    }

    @Test
    @DisplayName("Should successfully send adjuster report notification to Camunda")
    void shouldSuccessfullySendAdjusterReportNotificationToCamunda() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value("adjuster_report_received"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.businessKey").value(TEST_CLAIM_NUMBER))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully send customer invoice notification to Camunda")
    void shouldSuccessfullySendCustomerInvoiceNotificationToCamunda() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value("customer_invoice_received"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.businessKey").value(TEST_CLAIM_NUMBER))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, TEST_CUSTOMER_INVOICE_URL);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should route ADJUSTER_REPORT document type correctly")
    void shouldRouteAdjusterReportDocumentTypeCorrectly() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value("adjuster_report_received"))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaIntegrationService.notifyDocumentUploaded(
            TEST_CLAIM_NUMBER, DocumentType.ADJUSTER_REPORT, TEST_ADJUSTER_REPORT_URL);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should route INVOICE document type correctly")
    void shouldRouteInvoiceDocumentTypeCorrectly() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value("customer_invoice_received"))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaIntegrationService.notifyDocumentUploaded(
            TEST_CLAIM_NUMBER, DocumentType.INVOICE, TEST_CUSTOMER_INVOICE_URL);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle Camunda integration errors")
    void shouldHandleCamundaIntegrationErrors() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"type\":\"InvalidRequestException\",\"message\":\"Process not found\"}")
                .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() ->
            camundaIntegrationService.notifyAdjusterReportReceived(TEST_CLAIM_NUMBER, TEST_ADJUSTER_REPORT_URL))
            .isInstanceOf(CamundaIntegrationException.class);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle Camunda connection errors")
    void shouldHandleCamundaConnectionErrors() {
        // given
        mockServer.expect(MockRestRequestMatchers.requestTo(CAMUNDA_MESSAGE_ENDPOINT))
            .andRespond(MockRestResponseCreators.withException(
                new java.net.SocketTimeoutException("Connection timeout")));

        // when & then
        assertThatThrownBy(() ->
            camundaIntegrationService.notifyCustomerInvoiceReceived(TEST_CLAIM_NUMBER, TEST_CUSTOMER_INVOICE_URL))
            .isInstanceOf(CamundaConnectionException.class);

        // verify
        mockServer.verify();
    }
}
