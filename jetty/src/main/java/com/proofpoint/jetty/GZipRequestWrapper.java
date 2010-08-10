package com.proofpoint.jetty;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;

import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;

// TODO: handle request.getContentLength and getHeader("content-encoding")
class GZipRequestWrapper
        extends HttpServletRequestWrapper
{
    private final HttpServletRequest request;

    public GZipRequestWrapper(HttpServletRequest request)
    {
        super(request);
        this.request = request;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        return new ServletInputStreamFromInputStream(new GZIPInputStream(request.getInputStream()));
    }

    @Override
    public int getContentLength()
    {
        return -1;
    }

    @Override
    public String getHeader(String name)
    {
        if (equalsIgnoreCase(name, "content-length")) {
            return null;
        }

        // TODO: filter out content-coding
        
        return request.getHeader(name);
    }

    @Override
    public int getIntHeader(String name)
    {
        // TODO: filter out content-length
        return request.getIntHeader(name);
    }

    @Override
    public Enumeration getHeaderNames()
    {
        // TODO: filter out content-length & content-coding
        return request.getHeaderNames();
    }

    @Override
    public Enumeration getHeaders(String name)
    {
        // TODO: filter out content-length & content-coding
        return request.getHeaders(name);
    }
    

    private static class ServletInputStreamFromInputStream
            extends ServletInputStream
    {
        private final InputStream stream;

        public ServletInputStreamFromInputStream(InputStream stream) throws IOException
        {
            this.stream = stream;
        }

        public int read()
                throws IOException
        {
            return stream.read();
        }

        public int read(byte[] b)
                throws IOException
        {
            return stream.read(b);
        }

        public int read(byte[] b, int off, int len)
                throws IOException
        {
            return stream.read(b, off, len);
        }

        public long skip(long n)
                throws IOException
        {
            return stream.skip(n);
        }

        public int available()
                throws IOException
        {
            return stream.available();
        }

        public void close()
                throws IOException
        {
            stream.close();
        }

        public void mark(int readlimit)
        {
            stream.mark(readlimit);
        }

        public void reset()
                throws IOException
        {
            stream.reset();
        }

        public boolean markSupported()
        {
            return stream.markSupported();
        }
    }
}

