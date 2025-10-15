package io.airlift.http.client;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class TestFullJsonResponseHandler {
    private final JsonCodec<User> codec = JsonCodec.jsonCodec(User.class);
    private final FullJsonResponseHandler<User> handler = createFullJsonResponseHandler(codec);

    @Test
    public void testValidJson() {
        User user = new User("Joe", 25);
        String json = codec.toJson(user);
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertThat(response.hasValue()).isTrue();
        assertThat(response.getJsonBytes()).isEqualTo(json.getBytes(UTF_8));
        assertThat(response.getJson()).isEqualTo(json);
        assertThat(response.getValue().getName()).isEqualTo(user.getName());
        assertThat(response.getValue().getAge()).isEqualTo(user.getAge());

        assertThat(response.getJson()).isNotSameAs(response.getJson());
        assertThat(response.getJsonBytes()).isNotSameAs(response.getJsonBytes());
        assertThat(response.getResponseBytes()).isNotSameAs(response.getResponseBytes());
        assertThat(response.getResponseBody()).isNotSameAs(response.getResponseBody());

        assertThat(response.getResponseBytes()).isEqualTo(response.getJsonBytes());
        assertThat(response.getResponseBody()).isEqualTo(response.getJson());
    }

    @Test
    public void testInvalidJson() {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertThat(response.hasValue()).isFalse();
        assertThat(response.getException().getMessage())
                .isEqualTo(format("Unable to create %s from JSON response:\n[%s]", User.class, json));
        assertThat(response.getException()).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(response.getException())
                .hasStackTraceContaining(
                        "Invalid JSON bytes for [simple type, class io.airlift.http.client.TestFullJsonResponseHandler$User]");

        assertThat(response.getJsonBytes()).isEqualTo(json.getBytes(UTF_8));
        assertThat(response.getJson()).isEqualTo(json);

        assertThat(response.getResponseBytes()).isEqualTo(response.getJsonBytes());
        assertThat(response.getResponseBody()).isEqualTo(response.getJson());
    }

    @Test
    public void testInvalidJsonGetValue() {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        try {
            response.getValue();
            fail("expected exception");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Response does not contain a JSON value");
            assertThat(e.getCause()).isEqualTo(response.getException());

            assertThat(response.getJsonBytes()).isEqualTo(json.getBytes(UTF_8));
            assertThat(response.getJson()).isEqualTo(json);

            assertThat(response.getResponseBytes()).isEqualTo(response.getJsonBytes());
            assertThat(response.getResponseBody()).isEqualTo(response.getJson());
        }
    }

    @Test
    public void testNonJsonResponse() {
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));

        assertThat(response.hasValue()).isFalse();
        assertThat(response.getException()).isNull();
        assertThat(response.getJson()).isNull();
        assertThat(response.getJsonBytes()).isNull();

        assertThat(response.getResponseBytes()).isEqualTo("hello".getBytes(UTF_8));
        assertThat(response.getResponseBody()).isEqualTo("hello");
    }

    @Test
    public void testMissingContentType() {
        JsonResponse<User> response = handler.handle(
                null, new TestingResponse(OK, ImmutableListMultimap.<String, String>of(), "hello".getBytes(UTF_8)));

        assertThat(response.hasValue()).isFalse();
        assertThat(response.getException()).isNull();
        assertThat(response.getJson()).isNull();
        assertThat(response.getJsonBytes()).isNull();

        assertThat(response.getResponseBytes()).isEqualTo("hello".getBytes(UTF_8));
        assertThat(response.getResponseBody()).isEqualTo("hello");

        assertThat(response.getHeaders().isEmpty()).isTrue();
    }

    @Test
    public void testJsonErrorResponse() {
        String json = "{\"error\": true}";
        JsonResponse<User> response = handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json));

        assertThat(response.hasValue()).isTrue();
        assertThat(response.getJson()).isEqualTo(json);
        assertThat(response.getJsonBytes()).isEqualTo(json.getBytes(UTF_8));
        assertThat(response.getValue().getName()).isNull();
        assertThat(response.getValue().getAge()).isEqualTo(0);

        assertThat(response.getResponseBytes()).isEqualTo(response.getJsonBytes());
        assertThat(response.getResponseBody()).isEqualTo(response.getJson());
    }

    public static class User {
        private final String name;
        private final int age;

        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }

        @JsonProperty
        public String getName() {
            return name;
        }

        @JsonProperty
        public int getAge() {
            return age;
        }
    }
}
