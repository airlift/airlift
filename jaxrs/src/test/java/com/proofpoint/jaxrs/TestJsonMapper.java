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
package com.proofpoint.jaxrs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HttpHeaders;
import com.proofpoint.json.JsonCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

public class TestJsonMapper
{
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

    private void assertRoundTrip(String value)
            throws IOException
    {
        JsonCodec<String> jsonCodec = JsonCodec.jsonCodec(String.class);
        JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        MultivaluedMap<String, Object> headers = GuavaMultivaluedMap.create();
        jsonMapper.writeTo(value, String.class, null, null, null, headers, outputStream);

        String json = new String(outputStream.toByteArray(), Charsets.UTF_8);
        Assert.assertTrue(!json.contains("<"));
        Assert.assertTrue(!json.contains(">"));
        Assert.assertTrue(!json.contains("'"));
        Assert.assertTrue(!json.contains("&"));
        Assert.assertEquals(jsonCodec.fromJson(json), value);

        Assert.assertEquals(headers.getFirst(HttpHeaders.X_CONTENT_TYPE_OPTIONS), "nosniff");
    }

    @Test
    public void testEOFExceptionReturnsJsonMapperParsingException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            jsonMapper.readFrom(Object.class, Object.class, null, null, null, new InputStream()
            {
                @Override
                public int read()
                        throws IOException
                {
                    throw new EOFException("forced EOF Exception");
                }

                @Override
                public int read(byte[] b)
                        throws IOException
                {
                    throw new EOFException("forced EOF Exception");
                }

                @Override
                public int read(byte[] b, int off, int len)
                        throws IOException
                {
                    throw new EOFException("forced EOF Exception");
                }
            });
            Assert.fail("Should have thrown a JsonMapperParsingException");
        }
        catch (JsonMapperParsingException e) {
            Assert.assertTrue((e.getMessage()).startsWith("Invalid json for Java type"));
        }
    }

    @Test
    public void testJsonProcessingExceptionThrowsJsonMapperParsingException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            jsonMapper.readFrom(Object.class, Object.class, null, null, null, new InputStream()
            {
                @Override
                public int read()
                        throws IOException
                {
                    throw new TestingJsonProcessingException("forced JsonProcessingException");
                }

                @Override
                public int read(byte[] b)
                        throws IOException
                {
                    throw new TestingJsonProcessingException("forced JsonProcessingException");
                }

                @Override
                public int read(byte[] b, int off, int len)
                        throws IOException
                {
                    throw new TestingJsonProcessingException("forced JsonProcessingException");
                }
            });
            Assert.fail("Should have thrown a JsonMapperParsingException");
        }
        catch (JsonMapperParsingException e) {
            Assert.assertTrue((e.getMessage()).startsWith("Invalid json for Java type"));
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testOtherIOExceptionThrowsIOException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            jsonMapper.readFrom(Object.class, Object.class, null, null, null, new InputStream()
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
            });
            Assert.fail("Should have thrown an IOException");
        }
        catch (WebApplicationException e) {
            Assert.fail("Should not have received an IOException", e);
        }
    }

    @Test
    public void testBeanValidationThrowsBeanValidationException() throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            InputStream is = new ByteArrayInputStream("{}".getBytes());
            jsonMapper.readFrom(Object.class, JsonClass.class, null, null, null, is);
            Assert.fail("Should have thrown an BeanValidationException");
        }
        catch (BeanValidationException e) {
            Assert.assertEquals(e.getErrorMessages().size(), 2);
            Assert.assertTrue(e.getErrorMessages().contains("secondField may not be null"));
            Assert.assertTrue(e.getErrorMessages().contains("firstField may not be null"));
        }
    }

    private static class TestingJsonProcessingException extends JsonProcessingException
    {
        public TestingJsonProcessingException(String message)
        {
            super(message);
        }
    }

    public static class JsonClass
    {
        @NotNull
        private String firstField;

        @NotNull
        private String secondField;

        @JsonCreator
        private JsonClass(@JsonProperty("firstField") String firstField, @JsonProperty("secondField") String secondField)
        {
            this.firstField = firstField;
            this.secondField = secondField;
        }
    }
    static class GuavaMultivaluedMap<K, V> extends ForwardingMap<K, List<V>>
                implements MultivaluedMap<K, V>
    {
        private final ListMultimap<K, V> multimap;

        static <K, V> GuavaMultivaluedMap<K, V> create()
        {
            return new GuavaMultivaluedMap<>(ArrayListMultimap.<K, V>create());
        }

        private GuavaMultivaluedMap(ListMultimap<K, V> multimap)
        {
            this.multimap = multimap;
        }

        @Override
        public void putSingle(K key, V value)
        {
            multimap.removeAll(key);
            multimap.put(key, value);
        }

        @Override
        @SuppressWarnings({"RedundantCast"})
        protected Map<K, List<V>> delegate()
        {
            // forced cast
            return (Map<K, List<V>>) (Object) multimap.asMap();
        }

        @Override
        public void add(K key, V value)
        {
            multimap.put(key, value);
        }

        @Override
        public V getFirst(K key)
        {
            return Iterables.getFirst(multimap.get(key), null);
        }
    }
}
