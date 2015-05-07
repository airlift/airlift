package io.airlift.http.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.DefaultingJsonResponseHandler.createDefaultingJsonResponseHandler;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

public class TestDefaultingJsonResponseHandler
{
    private static final User DEFAULT_VALUE = new User("defaultUser", 998);
    private final JsonCodec<User> codec = JsonCodec.jsonCodec(User.class);
    private final DefaultingJsonResponseHandler<User> handler = createDefaultingJsonResponseHandler(codec, DEFAULT_VALUE);

    @Test
    public void testValidJson()
            throws Exception
    {
        User user = new User("Joe", 25);
        String json = codec.toJson(user);
        User response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertEquals(response.getName(), user.getName());
        assertEquals(response.getAge(), user.getAge());
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        User response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertSame(response, DEFAULT_VALUE);
    }

    @Test
    public void testException()
    {
        User response = handler.handleException(null, null);

        assertSame(response, DEFAULT_VALUE);
    }

    @Test
    public void testNonJsonResponse()
    {
        User response = handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));

        assertSame(response, DEFAULT_VALUE);
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        User response = handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json));

        assertSame(response, DEFAULT_VALUE);
    }

    public static class User
    {
        private final String name;
        private final int age;

        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("age") int age)
        {
            this.name = name;
            this.age = age;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public int getAge()
        {
            return age;
        }
    }
}
