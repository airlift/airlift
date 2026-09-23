package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.TestFullJsonResponseHandler.User;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.MULTI_STATUS;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.JsonResponseHandler.createSafeJsonResponseHandler;
import static io.airlift.http.client.JsonResponseHandler.createSuccessfulJsonResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJsonResponseHandler
{
    private final JsonCodec<User> codec = JsonCodec.jsonCodec(User.class);
    private final JsonResponseHandler<User> handler = createJsonResponseHandler(codec);

    @Test
    public void testValidJson()
    {
        User user = new User("Joe", 25);
        User response = handler.handle(null, mockResponse(OK, JSON_UTF_8, codec.toJson(user)));

        assertThat(response.getName()).isEqualTo(user.getName());
        assertThat(response.getAge()).isEqualTo(user.getAge());
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        try {
            handler.handle(null, mockResponse(OK, JSON_UTF_8, json));
        }
        catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Unable to create [simple type, %s] from JSON response: <%s>".formatted(User.class, json));
            assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void testSafeHandlerRedactsInvalidJsonAndCause()
    {
        String json = "{\"age\": \"secret-response-value\"}";
        JsonResponseHandler<User> safeHandler = createSafeJsonResponseHandler(codec, OK.code());

        assertThatThrownBy(() -> safeHandler.handle(null, mockResponse(OK, JSON_UTF_8, json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to create [simple type, %s] from JSON response", User.class)
                .hasMessageNotContaining(json)
                .hasMessageNotContaining("secret-response-value")
                .hasNoCause();
    }

    @Test
    public void testNonJsonResponse()
    {
        assertThatThrownBy(() -> handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello")))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected application/json; charset=utf-8 response from server but got text/plain; charset=utf-8");
    }

    @Test
    public void testMissingContentType()
    {
        assertThatThrownBy(() -> handler.handle(null, new TestingResponse(OK, ImmutableListMultimap.of(), "hello".getBytes(UTF_8))))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected application/json; charset=utf-8 response from server but got null");
    }

    @Test
    public void testSafeHandlerCapturesMalformedContentType()
    {
        String contentType = "malformed secret-content-type";
        String body = "secret-response-body";
        TestingResponse response = new TestingResponse(
                OK,
                ImmutableListMultimap.of(CONTENT_TYPE, contentType),
                body.getBytes(UTF_8));
        JsonResponseHandler<User> safeHandler = createSafeJsonResponseHandler(codec, OK.code());

        assertThatThrownBy(() -> safeHandler.handle(null, response))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessage("Expected application/json; charset=utf-8 response from server")
                .hasMessageNotContaining(contentType)
                .hasMessageNotContaining(body)
                .satisfies(throwable -> {
                    UnexpectedResponseException exception = (UnexpectedResponseException) throwable;
                    assertThat(exception.getStatusCode()).isEqualTo(OK.code());
                    assertThat(exception.getHeader(CONTENT_TYPE)).isEqualTo(contentType);
                    assertThat(exception.getResponseBody()).isEqualTo(body);
                });
    }

    @Test
    public void testLegacyHandlerPreservesMalformedContentTypeFailure()
    {
        String contentType = "malformed content type";
        TestingResponse response = new TestingResponse(
                OK,
                ImmutableListMultimap.of(CONTENT_TYPE, contentType),
                "response".getBytes(UTF_8));

        assertThatThrownBy(() -> handler.handle(null, response))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        assertThatThrownBy(() -> handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json)))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessage("Expected response code to be [200, 201, 202, 203, 204, 205, 206], but was 500");
    }

    @Test
    public void testSuccessfulHandlerAcceptsEntireTwoHundredRange()
    {
        JsonResponseHandler<User> successfulHandler = createSuccessfulJsonResponseHandler(codec);
        String json = codec.toJson(new User("Joe", 25));

        assertThat(successfulHandler.handle(null, mockResponse(MULTI_STATUS, JSON_UTF_8, json)))
                .extracting(User::getName)
                .isEqualTo("Joe");

        // the safe handler uses the same message as the compatible one
        assertThatThrownBy(() -> successfulHandler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json)))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessage("Expected response code to be 2xx, but was 500");
    }
}
