package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.TestFullJsonResponseHandler.User;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.lang.String.format;
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
            assertThat(e.getMessage()).isEqualTo(format("Unable to create %s from JSON response:\n[%s]", User.class, json));
            assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(e).hasStackTraceContaining("Invalid JSON bytes for [simple type, class io.airlift.http.client.TestFullJsonResponseHandler$User]");
        }
    }

    @Test
    public void testNonJsonResponse()
    {
        assertThatThrownBy(() -> handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello")))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Expected application/json response from server but got text/plain; charset=utf-8");
    }

    @Test
    public void testMissingContentType()
    {
        assertThatThrownBy(() -> handler.handle(null, new TestingResponse(OK, ImmutableListMultimap.<String, String>of(), "hello".getBytes(UTF_8))))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("Content-Type is not set for response");
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        assertThatThrownBy(() -> handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json)))
                .isInstanceOf(UnexpectedResponseException.class);
    }
}
