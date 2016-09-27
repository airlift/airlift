package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.TestFullJsonResponseHandler.User;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestJsonResponseHandler
{
    private final JsonCodec<User> codec = JsonCodec.jsonCodec(User.class);
    private final JsonResponseHandler<User> handler = createJsonResponseHandler(codec);

    @Test
    public void testValidJson()
    {
        User user = new User("Joe", 25);
        User response = handler.handle(null, mockResponse(OK, JSON_UTF_8, codec.toJson(user)));

        assertEquals(response.getName(), user.getName());
        assertEquals(response.getAge(), user.getAge());
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        try {
            handler.handle(null, mockResponse(OK, JSON_UTF_8, json));
        }
        catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), format("Unable to create %s from JSON response:\n[%s]", User.class, json));
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals(e.getCause().getMessage(), "Invalid JSON bytes for [simple type, class io.airlift.http.client.TestFullJsonResponseHandler$User]");
        }
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "Expected application/json response from server but got text/plain; charset=utf-8")
    public void testNonJsonResponse()
    {
        handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "Content-Type is not set for response")
    public void testMissingContentType()
    {
        handler.handle(null, new TestingResponse(OK, ImmutableListMultimap.<String, String>of(), "hello".getBytes(UTF_8)));
    }

    @Test(expectedExceptions = UnexpectedResponseException.class)
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json));
    }
}
