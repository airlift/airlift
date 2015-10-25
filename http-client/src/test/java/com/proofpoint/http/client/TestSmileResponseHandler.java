package com.proofpoint.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.SmileResponseHandler.createSmileResponseHandler;
import static com.proofpoint.http.client.TestFullJsonResponseHandler.User;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestSmileResponseHandler
{
    private static final MediaType MEDIA_TYPE_SMILE = MediaType.create("application", "x-jackson-smile");

    private JsonCodec<User> codec;
    private SmileResponseHandler<User> handler;

    @BeforeMethod
    public void setUp()
    {
        codec = JsonCodec.jsonCodec(User.class);
        handler = createSmileResponseHandler(codec);
    }

    @Test
    public void testValidSmile()
    {
        User response = handler.handle(null, createSmileResponse(OK, ImmutableMap.of(
                "name", "Joe",
                "age", 25,
                "extra", true
        )));

        assertEquals(response.getName(), "Joe");
        assertEquals(response.getAge(), 25);
    }

    @Test
    public void testInvalidSmile()
    {
        try {
            handler.handle(null, createSmileResponse(OK, ImmutableMap.of(
                    "age", "foo"
            )));
        }
        catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Unable to create " + User.class + " from SMILE response");
            assertInstanceOf(e.getCause(), InvalidFormatException.class);
            assertContains(e.getCause().getMessage(), "Can not construct instance of java.lang.Integer from String value 'foo': not a valid Integer value");
        }
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "Expected application/x-jackson-smile response from server but got text/plain; charset=utf-8")
    public void testNonJsonResponse()
    {
        handler.handle(null, mockResponse()
                .contentType(PLAIN_TEXT_UTF_8)
                .body("hello")
                .build());
    }

    @Test(expectedExceptions = UnexpectedResponseException.class, expectedExceptionsMessageRegExp = "Content-Type is not set for response")
    public void testMissingContentType()
    {
        handler.handle(null, mockResponse().body("hello").build());
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
            handler.handle(null, mockResponse()
                    .contentType(MEDIA_TYPE_SMILE)
                    .body(inputStream)
                    .build());
            fail("expected exception");
        }
        catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Error reading SMILE response from server");
            assertSame(e.getCause(), expectedException);
        }
    }

    private Response createSmileResponse(HttpStatus status, Object value)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            new ObjectMapper(new SmileFactory()).writeValue(outputStream, value);
        }
        catch (IOException e) {
            throw propagate(e);
        }
        return mockResponse()
                .status(status)
                .contentType(MEDIA_TYPE_SMILE)
                .body(outputStream.toByteArray())
                .build();
    }
}
