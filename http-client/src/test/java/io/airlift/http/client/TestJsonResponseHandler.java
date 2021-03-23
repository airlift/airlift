package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.TestFullJsonResponseHandler.User;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
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

    @Test(
            expectedExceptions = UnexpectedResponseException.class,
            expectedExceptionsMessageRegExp = "\\QExpected response code to be [200, 201, 202, 203, 204, 205, 206], but was 500; " +
                    "Response: [Failed to process your request: java.io.IOException: Out of disk space\n" +
                    "\tat java.nio.file.Files.createTempFile(Files.java:867)...] " +
                    "[4661696C656420746F2070726F6365737320796F757220726571756573743A206A6176612E696F2E494F457863657074696F6E3A204F7574" +
                    "206F66206469736B2073706163650A096174206A6176612E6E696F2E66696C652E46696C65732E63726561746554656D7046696C652846696" +
                    "C65732E6A6176613A38363729...]")
    public void testFailureResponse()
    {
        handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, PLAIN_TEXT_UTF_8, "Failed to process your request: java.io.IOException: Out of disk space\n" +
                "\tat java.nio.file.Files.createTempFile(Files.java:867)"));
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "\\QExpected application/json response from server but got text/plain; charset=utf-8; Response: [hello...] [68656C6C6F...]")
    public void testNonJsonResponse()
    {
        handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "\\QContent-Type is not set for response; Response: [hello...] [68656C6C6F...]")
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
