package tech.yildirim.camunda.documentmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * Comprehensive test suite for {@link CamundaRestClient}.
 * <p>
 * This test class covers all scenarios including:
 * <ul>
 *   <li>Successful message sending with various response codes</li>
 *   <li>HTTP client errors (4xx status codes)</li>
 *   <li>HTTP server errors (5xx status codes)</li>
 *   <li>Connection failures and network issues</li>
 *   <li>Input validation and edge cases</li>
 *   <li>URL normalization and configuration</li>
 * </ul>
 * </p>
 *
 * @author M. Ilker Yildirim
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CamundaRestClient Tests")
class CamundaRestClientTest {

    private static final String CAMUNDA_BASE_URL = "http://localhost:8080/engine-rest";
    private static final String MESSAGE_ENDPOINT_URL = CAMUNDA_BASE_URL + "/message";
    private static final String TEST_MESSAGE_NAME = "DocumentUploaded";
    private static final String TEST_BUSINESS_KEY = "CLAIM-2024-001";

    @Mock
    private RestTemplate mockRestTemplate;

    private CamundaRestClient camundaRestClient;

    @BeforeEach
    void setUp() {
        camundaRestClient = new CamundaRestClient(mockRestTemplate, CAMUNDA_BASE_URL);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize successfully with valid base URL")
        void shouldInitializeSuccessfullyWithValidBaseUrl() {
            // given
            String validUrl = "http://localhost:8080/engine-rest";

            // when & then
            CamundaRestClient client = new CamundaRestClient(mockRestTemplate, validUrl);
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("Should normalize URL by removing trailing slash")
        void shouldNormalizeUrlByRemovingTrailingSlash() {
            // given
            String urlWithTrailingSlash = "http://localhost:8080/engine-rest/";

            // when
            CamundaRestClient client = new CamundaRestClient(mockRestTemplate, urlWithTrailingSlash);

            // then
            assertThat(client).isNotNull();
            // URL normalization is tested implicitly through successful message sending
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null base URL")
        void shouldThrowExceptionForNullBaseUrl() {
            // when & then
            assertThatThrownBy(() -> new CamundaRestClient(mockRestTemplate, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Camunda base URL cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty base URL")
        void shouldThrowExceptionForEmptyBaseUrl() {
            // when & then
            assertThatThrownBy(() -> new CamundaRestClient(mockRestTemplate, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Camunda base URL cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for whitespace base URL")
        void shouldThrowExceptionForWhitespaceBaseUrl() {
            // when & then
            assertThatThrownBy(() -> new CamundaRestClient(mockRestTemplate, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Camunda base URL cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("Successful Message Sending Tests")
    class SuccessfulMessageSendingTests {

        @Test
        @DisplayName("Should send message successfully with NO_CONTENT response")
        void shouldSendMessageSuccessfullyWithNoContentResponse() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when & then
            camundaRestClient.sendMessage(messageDto);

            // verify
            verifyHttpEntityWasCreatedCorrectly();
        }

        @Test
        @DisplayName("Should send message successfully with OK response")
        void shouldSendMessageSuccessfullyWithOkResponse() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.OK);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when & then
            camundaRestClient.sendMessage(messageDto);

            // verify
            verifyHttpEntityWasCreatedCorrectly();
        }

        @Test
        @DisplayName("Should send message successfully with CREATED response")
        void shouldSendMessageSuccessfullyWithCreatedResponse() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.CREATED);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when & then
            camundaRestClient.sendMessage(messageDto);

            // verify
            verifyHttpEntityWasCreatedCorrectly();
        }

        @Test
        @DisplayName("Should create HTTP entity with correct headers")
        void shouldCreateHttpEntityWithCorrectHeaders() {
            // given
            CamundaMessageDTO messageDto = createComplexTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when
            camundaRestClient.sendMessage(messageDto);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<CamundaMessageDTO>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(mockRestTemplate).postForEntity(eq(MESSAGE_ENDPOINT_URL), httpEntityCaptor.capture(), eq(Void.class));

            HttpEntity<CamundaMessageDTO> capturedEntity = httpEntityCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();

            assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(headers.get("Accept")).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(headers.get("X-Requested-By")).contains("DocumentManager");
            assertThat(capturedEntity.getBody()).isEqualTo(messageDto);
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for null message DTO")
        void shouldThrowExceptionForNullMessageDto() {
            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message DTO cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null message name")
        void shouldThrowExceptionForNullMessageName() {
            // given
            CamundaMessageDTO messageDto = CamundaMessageDTO.builder()
                .messageName(null)
                .businessKey(TEST_BUSINESS_KEY)
                .build();

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message name cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty message name")
        void shouldThrowExceptionForEmptyMessageName() {
            // given
            CamundaMessageDTO messageDto = CamundaMessageDTO.builder()
                .messageName("")
                .businessKey(TEST_BUSINESS_KEY)
                .build();

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message name cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for whitespace message name")
        void shouldThrowExceptionForWhitespaceMessageName() {
            // given
            CamundaMessageDTO messageDto = CamundaMessageDTO.builder()
                .messageName("   ")
                .businessKey(TEST_BUSINESS_KEY)
                .build();

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message name cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("HTTP Client Error Tests")
    class HttpClientErrorTests {

        @Test
        @DisplayName("Should handle BAD_REQUEST with CamundaIntegrationException")
        void shouldHandleBadRequestError() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"InvalidRequestException\",\"message\":\"Invalid message format\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Invalid message format or correlation keys for message: " + TEST_MESSAGE_NAME)
                .hasMessageContaining(errorBody);
        }

        @Test
        @DisplayName("Should handle NOT_FOUND with CamundaIntegrationException")
        void shouldHandleNotFoundError() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"ProcessDefinitionNotFoundException\",\"message\":\"No process definition found\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("No matching process definition or instance found for message: " + TEST_MESSAGE_NAME)
                .hasMessageContaining(errorBody);
        }

        @Test
        @DisplayName("Should handle CONFLICT with CamundaIntegrationException")
        void shouldHandleConflictError() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"MessageCorrelationException\",\"message\":\"Multiple instances found\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Message correlation conflict for: " + TEST_MESSAGE_NAME)
                .hasMessageContaining(errorBody);
        }

        @Test
        @DisplayName("Should handle other client errors with generic message")
        void shouldHandleOtherClientErrors() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"UnauthorizedException\",\"message\":\"Authentication failed\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Client error correlating message: " + TEST_MESSAGE_NAME)
                .hasMessageContaining(errorBody);
        }
    }

    @Nested
    @DisplayName("HTTP Server Error Tests")
    class HttpServerErrorTests {

        @Test
        @DisplayName("Should handle INTERNAL_SERVER_ERROR with CamundaIntegrationException")
        void shouldHandleInternalServerError() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"ProcessEngineException\",\"message\":\"Database connection failed\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Camunda Engine server error for message: " + TEST_MESSAGE_NAME)
                .hasMessageContaining("Status: 500 INTERNAL_SERVER_ERROR");
        }

        @Test
        @DisplayName("Should handle SERVICE_UNAVAILABLE with CamundaIntegrationException")
        void shouldHandleServiceUnavailable() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();
            String errorBody = "{\"type\":\"ServiceUnavailableException\",\"message\":\"Service temporarily unavailable\"}";

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", errorBody.getBytes(), null));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Camunda Engine server error for message: " + TEST_MESSAGE_NAME)
                .hasMessageContaining("Status: 503 SERVICE_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("Connection Error Tests")
    class ConnectionErrorTests {

        @Test
        @DisplayName("Should handle connection timeout with CamundaConnectionException")
        void shouldHandleConnectionTimeout() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaConnectionException.class)
                .hasMessageContaining("Camunda Engine is unreachable for message: " + TEST_MESSAGE_NAME)
                .hasCauseInstanceOf(ResourceAccessException.class);
        }

        @Test
        @DisplayName("Should handle connection refused with CamundaConnectionException")
        void shouldHandleConnectionRefused() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaConnectionException.class)
                .hasMessageContaining("Camunda Engine is unreachable for message: " + TEST_MESSAGE_NAME)
                .hasCauseInstanceOf(ResourceAccessException.class);
        }
    }

    @Nested
    @DisplayName("Unexpected Error Tests")
    class UnexpectedErrorTests {

        @Test
        @DisplayName("Should handle unexpected RuntimeException with CamundaIntegrationException")
        void shouldHandleUnexpectedRuntimeException() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Unexpected error during Camunda integration for message: " + TEST_MESSAGE_NAME)
                .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should handle unexpected checked exception with CamundaIntegrationException")
        void shouldHandleUnexpectedCheckedException() {
            // given
            CamundaMessageDTO messageDto = createTestMessage();

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new IllegalStateException("Illegal state"));

            // when & then
            assertThatThrownBy(() -> camundaRestClient.sendMessage(messageDto))
                .isInstanceOf(CamundaIntegrationException.class)
                .hasMessageContaining("Unexpected error during Camunda integration for message: " + TEST_MESSAGE_NAME)
                .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("URL Handling Tests")
    class UrlHandlingTests {

        @Test
        @DisplayName("Should handle URL with trailing slash correctly")
        void shouldHandleUrlWithTrailingSlash() {
            // given
            String urlWithTrailingSlash = "http://localhost:8080/engine-rest/";
            CamundaRestClient clientWithTrailingSlash = new CamundaRestClient(mockRestTemplate, urlWithTrailingSlash);
            CamundaMessageDTO messageDto = createTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when & then
            clientWithTrailingSlash.sendMessage(messageDto);

            // verify URL was normalized correctly
            verify(mockRestTemplate).postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class));
        }

        @Test
        @DisplayName("Should handle URL without trailing slash correctly")
        void shouldHandleUrlWithoutTrailingSlash() {
            // given
            String urlWithoutTrailingSlash = "http://localhost:8080/engine-rest";
            CamundaRestClient clientWithoutTrailingSlash = new CamundaRestClient(mockRestTemplate, urlWithoutTrailingSlash);
            CamundaMessageDTO messageDto = createTestMessage();
            ResponseEntity<Void> successResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);

            when(mockRestTemplate.postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(successResponse);

            // when & then
            clientWithoutTrailingSlash.sendMessage(messageDto);

            // verify URL was handled correctly
            verify(mockRestTemplate).postForEntity(eq(MESSAGE_ENDPOINT_URL), any(HttpEntity.class), eq(Void.class));
        }
    }

    // Helper methods

    private CamundaMessageDTO createTestMessage() {
        return CamundaMessageDTO.builder()
            .messageName(TEST_MESSAGE_NAME)
            .businessKey(TEST_BUSINESS_KEY)
            .build();
    }

    private CamundaMessageDTO createComplexTestMessage() {
        CamundaMessageDTO message = CamundaMessageDTO.builder()
            .messageName(TEST_MESSAGE_NAME)
            .businessKey(TEST_BUSINESS_KEY)
            .build();

        message.addCorrelationKey("claimNumber", TEST_BUSINESS_KEY);
        message.addCorrelationKey("documentType", "INVOICE");
        message.addProcessVariable("documentId", 12345L);
        message.addProcessVariable("uploadedAt", LocalDateTime.now());
        message.addProcessVariable("fileSize", 1024000);
        message.addProcessVariable("approved", false);

        return message;
    }

    private void verifyHttpEntityWasCreatedCorrectly() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<CamundaMessageDTO>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(mockRestTemplate).postForEntity(eq(MESSAGE_ENDPOINT_URL), httpEntityCaptor.capture(), eq(Void.class));

        HttpEntity<CamundaMessageDTO> capturedEntity = httpEntityCaptor.getValue();
        HttpHeaders headers = capturedEntity.getHeaders();

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.get("Accept")).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(headers.get("X-Requested-By")).contains("DocumentManager");
    }
}
