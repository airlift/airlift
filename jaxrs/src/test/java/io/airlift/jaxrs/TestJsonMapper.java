/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.reflect.TypeToken;
import io.airlift.jaxrs.testing.GuavaMultivaluedMap;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipException;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class TestJsonMapper
{
    private static final JacksonMapper mapper = new JacksonMapper();
    private static final JsonMapper jsonMapper = new JsonMapper(new ObjectMapper());

    @Test
    public void testSuccess()
            throws IOException
    {
        assertRoundTrip("value");
        assertRoundTrip("<");
        assertRoundTrip(">");
        assertRoundTrip("&");
        assertRoundTrip("<>'&");
    }

    private static void assertRoundTrip(String value)
            throws IOException
    {
        JsonCodec<String> jsonCodec = JsonCodec.jsonCodec(String.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        MultivaluedMap<String, Object> headers = new GuavaMultivaluedMap<>();
        jsonMapper.writeTo(value, String.class, null, null, null, headers, outputStream);

        assertThat(jsonCodec.fromJson(outputStream.toString(UTF_8))).isEqualTo(value);
        assertThat(headers.getFirst(HttpHeaders.X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
    }

    @Test
    public void testJsonEofExceptionMapping()
            throws IOException
    {
        try {
            jsonMapper.readFrom(Object.class, Object.class, null, null, null, new ByteArrayInputStream("{".getBytes(UTF_8)));
            fail("Should have thrown an Exception");
        }
        catch (JsonParseException e) {
            assertBadRequestResponse(mapper.toResponse(e), BAD_REQUEST, "Could not read JSON value: Unexpected end-of-input: expected close marker for Object (start marker at [Source: (ByteArrayInputStream); line: 1, column: 1]) at location [Source: (ByteArrayInputStream); line: 1, column: 2]");
            assertThat(e.getMessage()).startsWith("Unexpected end-of-input: expected close marker for Object");
        }
    }

    @Test
    public void testJsonBindingExceptionMapping()
            throws IOException
    {
        try {
            jsonMapper.readFrom(Object.class, ExamplePojo.class, null, null, null, new ByteArrayInputStream("{\"notAField\": null}".getBytes(UTF_8)));
            fail("Should have thrown an Exception");
        }
        catch (JsonMappingException e) {
            assertBadRequestResponse(mapper.toResponse(e), INTERNAL_SERVER_ERROR, "Could not bind JSON value: Unrecognized field \"notAField\" (class io.airlift.jaxrs.TestJsonMapper$ExamplePojo), not marked as ignorable at location [Source: (ByteArrayInputStream); line: 1, column: 20]");
            assertThat(e.getMessage()).startsWith("Unrecognized field \"notAField\" (class io.airlift.jaxrs.TestJsonMapper$ExamplePojo), not marked as ignorable (one known property: \"value\"])\n" +
                    " at [Source: (ByteArrayInputStream); line: 1, column: 20] (through reference chain: io.airlift.jaxrs.TestJsonMapper$ExamplePojo[\"notAField\"])");
        }
    }

    @Test
    public void testJsonWriteExceptionMapping()
            throws IOException
    {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream(1);
            jsonMapper.writeTo(new OtherExamplePojo("nah", "bro"), List.class, new TypeToken<List<String>>() {}.getType(), null, null, new GuavaMultivaluedMap<>(), stream);
            System.out.println("stream: " + stream.toString());
            fail("Should have thrown an Exception");
        }
        catch (JsonProcessingException e) {
            assertBadRequestResponse(mapper.toResponse(e), INTERNAL_SERVER_ERROR, "Could not map JSON value: Incompatible types: declared root type ([collection type; class java.util.List, contains [simple type, class java.lang.String]]) vs `io.airlift.jaxrs.TestJsonMapper$OtherExamplePojo`");
            assertThat(e.getMessage()).startsWith("Incompatible types: declared root type ([collection type; class java.util.List, contains [simple type, class java.lang.String]]) vs `io.airlift.jaxrs.TestJsonMapper$OtherExamplePojo`");
        }
    }

    @Test
    public void testOtherIOExceptionThrowsIOException()
    {
        try {
            assertThatThrownBy(() -> jsonMapper.readFrom(Object.class, Object.class, null, null, null, new InputStream()
            {
                @Override
                public int read()
                        throws IOException
                {
                    throw new ZipException("forced ZipException");
                }

                @Override
                public int read(byte[] b)
                        throws IOException
                {
                    throw new ZipException("forced ZipException");
                }

                @Override
                public int read(byte[] b, int off, int len)
                        throws IOException
                {
                    throw new ZipException("forced ZipException");
                }
            })).isInstanceOf(ZipException.class);
        }
        catch (WebApplicationException e) {
            fail("Should not have received a WebApplicationException", e);
        }
    }

    private static void assertBadRequestResponse(Response response, Response.Status code, String expectedMessage)
    {
        assertThat(response.getStatus()).isEqualTo(code.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(String.class);
        assertThat((String) response.getEntity()).contains(expectedMessage);
    }

    private record ExamplePojo(String value)
    {
    }

    private record OtherExamplePojo(String value, String value2)
    {
    }
}
