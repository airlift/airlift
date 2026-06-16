/*
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

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.google.common.net.HttpHeaders;
import com.google.common.reflect.TypeToken;
import io.airlift.jaxrs.testing.GuavaMultivaluedMap;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class TestJaxRsYamlMapper
{
    private static final JaxRsYamlMapper yamlMapper = new JaxRsYamlMapper();

    @Test
    public void testSuccess()
            throws IOException
    {
        assertRoundTrip("value");
        assertRoundTrip("multi\nline\nstring");
    }

    private static void assertRoundTrip(String value)
            throws IOException
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        MultivaluedMap<String, Object> headers = new GuavaMultivaluedMap<>();
        yamlMapper.writeTo(value, String.class, null, null, null, headers, outputStream);

        Object roundTripped = yamlMapper.readFrom(
                Object.class,
                String.class,
                null,
                null,
                null,
                new ByteArrayInputStream(outputStream.toByteArray()));
        assertThat(roundTripped).isEqualTo(value);
        assertThat(headers.getFirst(HttpHeaders.X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
    }

    @Test
    public void testYamlEofExceptionMapping()
    {
        // A truncated mapping (`name:` with no continuation) is interpreted as EOF.
        assertThatThrownBy(() -> yamlMapper.readFrom(
                Object.class,
                ExamplePojo.class,
                null,
                null,
                null,
                new ByteArrayInputStream("name:\n  unfinished:".getBytes(UTF_8))))
                .isInstanceOf(YamlParsingException.class);
    }

    @Test
    public void testYamlBindingExceptionMapping()
    {
        assertThatThrownBy(() -> yamlMapper.readFrom(
                Object.class,
                ExamplePojo.class,
                null,
                null,
                null,
                new ByteArrayInputStream("value: [not, a, string]".getBytes(UTF_8))))
                .isInstanceOf(YamlParsingException.class);
    }

    @Test
    public void testYamlWriteExceptionMapping()
    {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream(1);
            yamlMapper.writeTo(new OtherExamplePojo("nah", "bro"), List.class, new TypeToken<List<String>>() {}.getType(), null, null, new GuavaMultivaluedMap<>(), stream);
            fail("Should have thrown an Exception");
        }
        catch (InvalidDefinitionException e) {
            // intended
        }
        catch (IOException e) {
            if (e instanceof YamlParsingException) {
                fail("yamlMapper.writeTo() should not throw YamlParsingException");
            }
            throw new AssertionError(e);
        }
    }

    @Test
    public void testOtherIOExceptionThrowsIOException()
    {
        try {
            assertThatThrownBy(() -> yamlMapper.readFrom(Object.class, Object.class, null, null, null, new InputStream()
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

    @Test
    public void testParsingExceptionMapperReturnsJsonContentType()
    {
        // Errors are returned as JSON regardless of the request format.
        YamlParsingExceptionMapper mapper = new YamlParsingExceptionMapper();
        YamlParsingException exception = new YamlParsingException(new IOException("boom"));

        try (jakarta.ws.rs.core.Response response = mapper.toResponse(exception)) {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getMediaType().toString()).isEqualTo(jakarta.ws.rs.core.MediaType.APPLICATION_JSON);
            assertThat(response.getEntity()).isInstanceOf(JsonError.class);
        }
    }

    private record ExamplePojo(String value) {}

    private record OtherExamplePojo(String value, String value2) {}
}
