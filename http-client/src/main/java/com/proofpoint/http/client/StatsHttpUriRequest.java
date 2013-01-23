package com.proofpoint.http.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.CountingOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.CloneUtils;
import org.apache.http.protocol.HTTP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map.Entry;

public abstract class StatsHttpUriRequest
        extends HttpRequestBase
{
    public static StatsHttpUriRequest createGenericHttpRequest(Request request)
    {
        if (request.getBodyGenerator() == null) {
            return new GenericHttpRequest(request);
        }
        else {
            return new GenericEntityHttpRequest(request);
        }
    }

    public long getBytesWritten()
    {
        return 0;
    }

    private static class GenericHttpRequest
            extends StatsHttpUriRequest
    {
        private final String method;

        GenericHttpRequest(Request request)
        {
            this.method = request.getMethod();
            setURI(request.getUri());

            for (Entry<String, String> entry : request.getHeaders().entries()) {
                addHeader(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public String getMethod()
        {
            return method;
        }
    }

    private static class GenericEntityHttpRequest
            extends GenericHttpRequest
            implements HttpEntityEnclosingRequest
    {
        private HttpEntity entity;
        private CountingOutputStream countingOutputStream;

        public GenericEntityHttpRequest(final Request request)
        {
            super(request);
            Preconditions.checkArgument(request.getBodyGenerator() != null, "Generic entity request must have a body");
            entity = new HttpEntity()
            {
                @Override
                public boolean isRepeatable()
                {
                    return true;
                }

                @Override
                public boolean isChunked()
                {
                    return true;
                }

                @Override
                public long getContentLength()
                {
                    return -1;
                }

                @Override
                public Header getContentType()
                {
                    return null;
                }

                @Override
                public Header getContentEncoding()
                {
                    return null;
                }

                @Override
                public InputStream getContent()
                        throws IOException, IllegalStateException
                {
                    // TODO: find a better way... async interface uses this
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    writeTo(out);
                    return new ByteArrayInputStream(out.toByteArray());
                }

                @Override
                public void writeTo(OutputStream out)
                        throws IOException
                {
                    try {
                        countingOutputStream = new CountingOutputStream(out);
                        request.getBodyGenerator().write(countingOutputStream);
                    }
                    catch (Exception e) {
                        Throwables.propagateIfPossible(e, IOException.class);
                        throw new IOException(e);
                    }
                }

                @Override
                public boolean isStreaming()
                {
                    return true;
                }

                @Override
                public void consumeContent()
                {
                }
            };
        }

        public HttpEntity getEntity()
        {
            return this.entity;
        }

        public void setEntity(HttpEntity entity)
        {
            throw new UnsupportedOperationException();
        }

        public boolean expectContinue()
        {
            Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
            return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
        }

        public long getBytesWritten()
        {
            if (countingOutputStream == null) {
                return 0;
            }
            return countingOutputStream.getCount();
        }

        @Override
        public Object clone()
                throws CloneNotSupportedException
        {
            GenericEntityHttpRequest clone = (GenericEntityHttpRequest) super.clone();
            if (this.entity != null) {
                clone.entity = (HttpEntity) CloneUtils.clone(this.entity);
            }
            return clone;
        }
    }
}
