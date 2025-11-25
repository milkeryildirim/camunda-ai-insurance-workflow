package tech.yildirim.camunda.documentmanager.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
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
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * Integration tests for {@link CamundaRestClient} using {@link MockRestServiceServer}.
 * <p>
 * This test class provides end-to-end testing of the CamundaRestClient with actual
 * HTTP communication simulation, testing the complete flow including:
 * <ul>
 *   <li>Request serialization and deserialization</li>
 *   <li>HTTP headers and content type handling</li>
 *   <li>Response status code interpretation</li>
 *   <li>Error response body parsing</li>
 *   <li>Spring Boot configuration integration</li>
 * </ul>
 * </p>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "camunda.bpm.client.base-url=http://localhost:8080/engine-rest",
    "logging.level.tech.yildirim.camunda.documentmanager.integration=DEBUG"
})
@DisplayName("CamundaRestClient Integration Tests")
class CamundaRestClientIntegrationTest {

    private static final String MESSAGE_ENDPOINT_URL = "http://localhost:8080/engine-rest/message";
    private static final String TEST_MESSAGE_NAME = "DocumentProcessed";
    private static final String TEST_BUSINESS_KEY = "CLAIM-INTEGRATION-001";

    @Autowired
    private CamundaRestClient camundaRestClient;

    @Autowired
    @Qualifier("camundaRestTemplate")
    private RestTemplate camundaRestTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(camundaRestTemplate);
    }

    @Test
    @DisplayName("Should successfully send message and handle NO_CONTENT response")
    void shouldSuccessfullySendMessageWithNoContentResponse() {
        // given
        CamundaMessageDTO messageDto = createIntegrationTestMessage();

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(MockRestRequestMatchers.header("Accept", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(MockRestRequestMatchers.header("X-Requested-By", "DocumentManager"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value(TEST_MESSAGE_NAME))
            .andExpect(MockRestRequestMatchers.jsonPath("$.businessKey").value(TEST_BUSINESS_KEY))
            .andExpect(MockRestRequestMatchers.jsonPath("$.correlationKeys.claimNumber.value").value(TEST_BUSINESS_KEY))
            .andExpect(MockRestRequestMatchers.jsonPath("$.correlationKeys.claimNumber.type").value("String"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.documentId.value").value(99999))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.documentId.type").value("Long"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.urgent.value").value(true))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.urgent.type").value("Boolean"))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaRestClient.sendMessage(messageDto);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully send message and handle OK response")
    void shouldSuccessfullySendMessageWithOkResponse() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value(TEST_MESSAGE_NAME))
            .andExpect(MockRestRequestMatchers.jsonPath("$.businessKey").value(TEST_BUSINESS_KEY))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.OK));

        // when & then
        camundaRestClient.sendMessage(messageDto);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle BAD_REQUEST with detailed error message")
    void shouldHandleBadRequestWithDetailedErrorMessage() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();
        String errorResponseBody = """
            {
                "type": "InvalidRequestException",
                "message": "Cannot correlate message 'DocumentProcessed': No process definition or execution matches the parameters",
                "code": 400
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST)
                .body(errorResponseBody)
                .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
            .isInstanceOf(CamundaIntegrationException.class)
            .hasMessageContaining("Invalid message format or correlation keys for message: " + TEST_MESSAGE_NAME)
            .hasMessageContaining("Cannot correlate message");

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle NOT_FOUND with process definition error")
    void shouldHandleNotFoundWithProcessDefinitionError() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();
        String errorResponseBody = """
            {
                "type": "ProcessDefinitionNotFoundException",
                "message": "No matching process definition found for message name 'DocumentProcessed'",
                "code": 404
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND)
                .body(errorResponseBody)
                .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
            .isInstanceOf(CamundaIntegrationException.class)
            .hasMessageContaining("No matching process definition or instance found for message: " + TEST_MESSAGE_NAME)
            .hasMessageContaining("No matching process definition found");

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle CONFLICT with correlation error")
    void shouldHandleConflictWithCorrelationError() {
        // given
        CamundaMessageDTO messageDto = createIntegrationTestMessage();
        String errorResponseBody = """
            {
                "type": "MessageCorrelationException",
                "message": "Cannot correlate message 'DocumentProcessed': Found multiple executions",
                "code": 409
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.CONFLICT)
                .body(errorResponseBody)
                .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
            .isInstanceOf(CamundaIntegrationException.class)
            .hasMessageContaining("Message correlation conflict for: " + TEST_MESSAGE_NAME)
            .hasMessageContaining("Found multiple executions");

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle INTERNAL_SERVER_ERROR with engine error")
    void shouldHandleInternalServerErrorWithEngineError() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();
        String errorResponseBody = """
            {
                "type": "ProcessEngineException",
                "message": "Database connection failed during message correlation",
                "code": 500
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponseBody)
                .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
            .isInstanceOf(CamundaIntegrationException.class)
            .hasMessageContaining("Camunda Engine server error for message: " + TEST_MESSAGE_NAME)
            .hasMessageContaining("Status: 500 INTERNAL_SERVER_ERROR");

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle network timeout error")
    void shouldHandleNetworkTimeoutError() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withException(
                new java.net.SocketTimeoutException("Read timeout")));

        // when & then
        assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
            .isInstanceOf(CamundaConnectionException.class)
            .hasMessageContaining("Camunda Engine is unreachable for message: " + TEST_MESSAGE_NAME);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should send message with complex correlation and process variables")
    void shouldSendMessageWithComplexVariables() {
        // given
        CamundaMessageDTO messageDto = createComplexIntegrationTestMessage();

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.jsonPath("$.messageName").value("ComplexDocumentEvent"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.businessKey").value("COMPLEX-CLAIM-001"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.correlationKeys.claimNumber.value").value("COMPLEX-CLAIM-001"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.correlationKeys.documentType.value").value("ADJUSTER_REPORT"))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.documentId.value").value(555555))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.fileSize.value").value(2048000))
            .andExpect(MockRestRequestMatchers.jsonPath("$.processVariables.priority.value").value("HIGH"))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaRestClient.sendMessage(messageDto);

        // verify
        mockServer.verify();
    }

    @Test
    @DisplayName("Should validate request structure and content type")
    void shouldValidateRequestStructureAndContentType() {
        // given
        CamundaMessageDTO messageDto = createSimpleTestMessage();

        mockServer.expect(MockRestRequestMatchers.requestTo(MESSAGE_ENDPOINT_URL))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.header("Content-Type", "application/json"))
            .andExpect(MockRestRequestMatchers.header("Accept", "application/json"))
            .andExpect(MockRestRequestMatchers.header("X-Requested-By", "DocumentManager"))
            .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));

        // when & then
        camundaRestClient.sendMessage(messageDto);

        // verify
        mockServer.verify();
    }

    // Helper methods

    private CamundaMessageDTO createSimpleTestMessage() {
        return CamundaMessageDTO.builder()
            .messageName(TEST_MESSAGE_NAME)
            .businessKey(TEST_BUSINESS_KEY)
            .build();
    }

    private CamundaMessageDTO createIntegrationTestMessage() {
        CamundaMessageDTO message = CamundaMessageDTO.builder()
            .messageName(TEST_MESSAGE_NAME)
            .businessKey(TEST_BUSINESS_KEY)
            .build();

        message.addCorrelationKey("claimNumber", TEST_BUSINESS_KEY);
        message.addProcessVariable("documentId", 99999L);
        message.addProcessVariable("urgent", true);

        return message;
    }

    private CamundaMessageDTO createComplexIntegrationTestMessage() {
        CamundaMessageDTO message = CamundaMessageDTO.builder()
            .messageName("ComplexDocumentEvent")
            .businessKey("COMPLEX-CLAIM-001")
            .build();

        message.addCorrelationKey("claimNumber", "COMPLEX-CLAIM-001");
        message.addCorrelationKey("documentType", "ADJUSTER_REPORT");
        message.addProcessVariable("documentId", 555555L);
        message.addProcessVariable("uploadedAt", LocalDateTime.now().toString());
        message.addProcessVariable("fileSize", 2048000);
        message.addProcessVariable("priority", "HIGH");
        message.addProcessVariable("autoApproved", false);
        message.addProcessVariable("reviewerComments", "Requires manual review");

        return message;
    }
}
