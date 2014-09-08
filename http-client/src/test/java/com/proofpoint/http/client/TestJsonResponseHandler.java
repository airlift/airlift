package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.proofpoint.http.client.testing.TestingResponse;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.TestFullJsonResponseHandler.User;
import static com.proofpoint.http.client.testing.TestingResponse.contentType;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestJsonResponseHandler
{
    private JsonCodec<User> codec;
    private JsonResponseHandler<User> handler;

    @BeforeMethod
    public void setUp()
    {
        codec = JsonCodec.jsonCodec(User.class);
        handler = createJsonResponseHandler(codec);
    }

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
            assertEquals(e.getMessage(), "Unable to create " + User.class + " from JSON response:\n" + json);
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals(e.getCause().getMessage(), "Invalid [simple type, class com.proofpoint.http.client.TestFullJsonResponseHandler$User] json bytes");
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

    @Test
    public void testJsonReadException()
            throws IOException
    {
        InputStream inputStream = mock(InputStream.class);
        IOException expectedException = new IOException("test exception");
        when(inputStream.read()).thenThrow(expectedException);
        when(inputStream.read(any(byte[].class))).thenThrow(expectedException);
        when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(expectedException);

        try {
            handler.handle(null, new TestingResponse(OK, contentType(JSON_UTF_8), inputStream));
            fail("expected exception");
        }
        catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Error reading JSON response from server");
            assertSame(e.getCause(), expectedException);
        }
    }
}
