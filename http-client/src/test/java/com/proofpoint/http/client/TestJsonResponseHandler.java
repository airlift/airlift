package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.proofpoint.http.client.testing.TestingResponse;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.TestFullJsonResponseHandler.User;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static org.testng.Assert.assertEquals;

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

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "\\QInvalid [simple type, class com.proofpoint.http.client.TestFullJsonResponseHandler$User] json string\\E")
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        handler.handle(null, mockResponse(OK, JSON_UTF_8, json));
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
