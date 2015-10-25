package com.proofpoint.http.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.FullSmileResponseHandler.SmileResponse;
import static com.proofpoint.http.client.FullSmileResponseHandler.createFullSmileResponseHandler;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class TestFullSmileResponseHandler
{
    private static final MediaType MEDIA_TYPE_SMILE = MediaType.create("application", "x-jackson-smile");

    private JsonCodec<User> codec;
    private FullSmileResponseHandler<User> handler;

    @BeforeMethod
    public void setUp()
    {
        codec = JsonCodec.jsonCodec(User.class);
        handler = createFullSmileResponseHandler(codec);
    }

    @Test
    public void testValidSmile()
    {
        SmileResponse<User> response = handler.handle(null, createSmileResponse(OK, ImmutableMap.of(
                "name", "Joe",
                "age", 25,
                "extra", true
        )));

        assertTrue(response.hasValue());
        assertEquals(response.getValue().getName(), "Joe");
        assertEquals(response.getValue().getAge(), 25);
    }

    @Test
    public void testInvalidSmile()
    {
        SmileResponse<User> response = handler.handle(null, createSmileResponse(OK, ImmutableMap.of(
                "age", "foo"
        )));

        assertFalse(response.hasValue());
        assertEquals(response.getException().getMessage(),
                "Unable to create " + User.class + " from SMILE response");
        assertInstanceOf(response.getException().getCause(), InvalidFormatException.class);
        assertContains(response.getException().getCause().getMessage(),
                "Can not construct instance of java.lang.Integer from String value 'foo': not a valid Integer value");
    }

    @Test
    public void testInvalidSmileGetValue()
    {
        SmileResponse<User> response = handler.handle(null, createSmileResponse(OK, ImmutableMap.of(
                "age", "foo"
        )));

        try {
            response.getValue();
            fail("expected exception");
        }
        catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Response does not contain a SMILE value");
            assertSame(e.getCause(), response.getException());
        }
    }

    @Test
    public void testNonSmileResponse()
    {
        SmileResponse<User> response = handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));

        assertFalse(response.hasValue());
        assertNull(response.getException());
    }

    @Test
    public void testMissingContentType()
    {
        SmileResponse<User> response = handler.handle(null, mockResponse().body("hello").build());

        assertFalse(response.hasValue());
        assertNull(response.getException());
        assertTrue(response.getHeaders().isEmpty());
    }

    @Test
    public void testSmileErrorResponse()
    {
        SmileResponse<User> response = handler.handle(null, createSmileResponse(INTERNAL_SERVER_ERROR, ImmutableMap.of(
                "error", true
        )));

        assertTrue(response.hasValue());
        assertNull(response.getValue().getName());
        assertEquals(response.getValue().getAge(), 0);
    }

    @Test
    public void testSmileReadException()
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

    private static Response createSmileResponse(HttpStatus status, Object value)
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

    static class User
    {
        private final String name;
        private final int age;

        @JsonCreator
        User(@JsonProperty("name") String name, @JsonProperty("age") int age)
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
