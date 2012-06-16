package io.airlift.jaxrs;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

public class TestJsonMapper
{
    @Test
    public void testEOFExceptionTeturnsWebAppException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            InputStream is = new ByteArrayInputStream("foo".getBytes());
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
            Assert.fail("Should have thrown a WebApplicationException");
        }
        catch (WebApplicationException e) {
            Assert.assertEquals(e.getResponse().getStatus(), Status.BAD_REQUEST.getStatusCode());
            Assert.assertTrue(((String) e.getResponse().getEntity()).startsWith("Invalid json for Java type"));
        }
    }

    @Test
    public void testJsonProcessingExceptionThrowsWebAppException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            InputStream is = new ByteArrayInputStream("foo".getBytes());
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
            Assert.fail("Should have thrown a WebApplicationException");
        }
        catch (WebApplicationException e) {
            Assert.assertEquals(e.getResponse().getStatus(), Status.BAD_REQUEST.getStatusCode());
            Assert.assertTrue(((String) e.getResponse().getEntity()).startsWith("Invalid json for Java type"));
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testOtherIOExceptionThrowsIOException()
            throws IOException
    {
        try {
            JsonMapper jsonMapper = new JsonMapper(new ObjectMapper(), null);
            InputStream is = new ByteArrayInputStream("foo".getBytes());
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
            Assert.fail("Should not have received a WebApplicationException", e);
        }
    }

    private static class TestingJsonProcessingException extends JsonProcessingException
    {
        public TestingJsonProcessingException(String message)
        {
            super(message);
        }
    }
}
