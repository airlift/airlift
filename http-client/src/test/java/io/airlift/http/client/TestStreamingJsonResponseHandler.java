package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.StreamingJsonResponseHandler.createStreamingJsonResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStreamingJsonResponseHandler
{
    private final JsonCodec<User> codec = JsonCodec.jsonCodec(User.class);
    private final StreamingJsonResponseHandler<User> handler = createStreamingJsonResponseHandler(codec);

    @Test
    public void testValidJson()
    {
        User user = new User("Joe", 25);
        String json = codec.toJson(user);
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertThat(response)
                .isInstanceOf(JsonResponse.JsonValue.class);

        JsonResponse.JsonValue<User> value = (JsonResponse.JsonValue<User>) response;
        assertThat(value.jsonValue()).isEqualTo(user);
        assertThat(value.statusCode()).isEqualTo(OK.code());
        assertThat(value.request()).isNull();
        assertThat(value.bytesRead()).isEqualTo(34);
        assertThat(value.headers().asMap())
                .containsEntry(HeaderName.of("Content-Type"), List.of(JSON_UTF_8.toString()));
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertThat(response)
                .isInstanceOf(JsonResponse.Exception.class);

        JsonResponse.Exception<User> exception = (JsonResponse.Exception<User>) response;
        assertThat(exception.request()).isNull();
        assertThat(exception.throwable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON bytes for [simple type, class io.airlift.http.client.TestStreamingJsonResponseHandler$User]");
    }

    @Test
    public void testInvalidJsonGetValue()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertThatThrownBy(response::jsonValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Response does not contain a JSON value")
                .hasStackTraceContaining("Cannot deserialize value of type `int` from String \"foo\": not a valid `int` value");

        assertThat(response)
                .isInstanceOf(JsonResponse.Exception.class);

        JsonResponse.Exception<User> exception = (JsonResponse.Exception<User>) response;

        assertThat(exception.throwable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON bytes for [simple type, class io.airlift.http.client.TestStreamingJsonResponseHandler$User]");
    }

    @Test
    public void testNonJsonResponse()
    {
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));

        assertThatThrownBy(response::jsonValue)
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Response does not contain a JSON value");

        assertThat(response).isInstanceOf(JsonResponse.NonJsonBytes.class);

        JsonResponse.NonJsonBytes<User> bytes = (JsonResponse.NonJsonBytes<User>) response;
        assertThat(bytes.body()).isEqualTo("hello".getBytes(UTF_8));
        assertThat(bytes.stringValue()).isEqualTo("hello");
        assertThat(bytes.statusCode()).isEqualTo(OK.code());
        assertThat(bytes.charset()).isEqualTo(UTF_8);
        assertThat(bytes.throwable())
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected application/json response from server but got text/plain; charset=utf-8");
    }

    @Test
    public void testMissingContentType()
    {
        JsonResponse<User> response = handler.handle(null,
                new TestingResponse(OK, ImmutableListMultimap.of(), "hello".getBytes(UTF_8)));

        assertThatThrownBy(response::jsonValue)
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Response does not contain a JSON value");

        JsonResponse.NonJsonBytes<User> bytes = (JsonResponse.NonJsonBytes<User>) response;
        assertThat(bytes.body()).isEqualTo("hello".getBytes(UTF_8));
        assertThat(bytes.stringValue()).isEqualTo("hello");
        assertThat(bytes.statusCode()).isEqualTo(OK.code());
        assertThat(bytes.charset()).isEqualTo(UTF_8);
        assertThat(bytes.throwable())
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Content-Type is not set for response");
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        JsonResponse<User> response = handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json));

        assertThatThrownBy(response::jsonValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Response does not contain a JSON value")
                .hasStackTraceContaining("Invalid JSON bytes for [simple type, class io.airlift.http.client.TestStreamingJsonResponseHandler$User]");

        assertThat(response)
                .isInstanceOf(JsonResponse.Exception.class);

        JsonResponse.Exception<User> exception = (JsonResponse.Exception<User>) response;

        assertThat(exception.throwable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON bytes for [simple type, class io.airlift.http.client.TestStreamingJsonResponseHandler$User]");
    }

    public record User(String name, int age)
    {
        public User
        {
            requireNonNull(name, "name is null");
        }
    }
}
