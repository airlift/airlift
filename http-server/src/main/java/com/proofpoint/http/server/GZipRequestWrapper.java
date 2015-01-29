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
package com.proofpoint.http.server;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import static com.google.common.base.Ascii.equalsIgnoreCase;

class GZipRequestWrapper
        extends HttpServletRequestWrapper
{
    private final HttpServletRequest request;

    GZipRequestWrapper(HttpServletRequest request)
    {
        super(request);
        this.request = request;
    }

    @Override
    public ServletInputStream getInputStream()
            throws IOException
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
        if (equalsIgnoreCase(name, "content-length") || equalsIgnoreCase(name, "content-encoding")) {
            return null;
        }

        return request.getHeader(name);
    }

    @Override
    public int getIntHeader(String name)
    {
        if (equalsIgnoreCase(name, "content-length")) {
            return -1;
        }
        return request.getIntHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return new FilteringEnumeration(request.getHeaderNames());
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        if (equalsIgnoreCase(name, "content-length") || equalsIgnoreCase(name, "content-encoding")) {
            return new Enumeration<String>()
            {
                @Override
                public boolean hasMoreElements()
                {
                    return false;
                }

                @Override
                public String nextElement()
                {
                    throw new NoSuchElementException();
                }
            };
        }

        return request.getHeaders(name);
    }


    private static class ServletInputStreamFromInputStream
            extends ServletInputStream
    {
        private final InputStream stream;

        ServletInputStreamFromInputStream(InputStream stream)
        {
            this.stream = stream;
        }

        @Override
        public int read()
                throws IOException
        {
            return stream.read();
        }

        @Override
        public int read(byte[] b)
                throws IOException
        {
            return stream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len)
                throws IOException
        {
            return stream.read(b, off, len);
        }

        @Override
        public long skip(long n)
                throws IOException
        {
            return stream.skip(n);
        }

        @Override
        public int available()
                throws IOException
        {
            return stream.available();
        }

        @Override
        public void close()
                throws IOException
        {
            stream.close();
        }

        @Override
        public void mark(int readLimit)
        {
            stream.mark(readLimit);
        }

        @Override
        public void reset()
                throws IOException
        {
            stream.reset();
        }

        @Override
        public boolean markSupported()
        {
            return stream.markSupported();
        }

        @Override
        public boolean isFinished()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReady()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReadListener(ReadListener readListener)
        {
            throw new UnsupportedOperationException();
        }

    }

    private static class FilteringEnumeration implements Enumeration<String>
    {
        private final Enumeration<String> delegate;
        MoreElementsState moreElementsState;
        String nextElement;

        private enum MoreElementsState
        {
            UNKNOWN, YES, NO
        }

        public FilteringEnumeration(Enumeration<String> headerNames)
        {
            delegate = headerNames;
            moreElementsState = MoreElementsState.UNKNOWN;
        }

        @Override
        public boolean hasMoreElements()
        {
            if (moreElementsState == MoreElementsState.UNKNOWN) {
                lookAhead();
            }
            return moreElementsState == MoreElementsState.YES;
        }

        @Override
        public String nextElement()
        {
            if (moreElementsState == MoreElementsState.UNKNOWN) {
                lookAhead();
            }
            if (moreElementsState == MoreElementsState.NO) {
                throw new NoSuchElementException();
            }
            moreElementsState = MoreElementsState.UNKNOWN;
            return nextElement;
        }

        private void lookAhead()
        {
            do {
                if (!delegate.hasMoreElements()) {
                    moreElementsState = MoreElementsState.NO;
                    return;
                }
                nextElement = delegate.nextElement();
            } while (equalsIgnoreCase(nextElement, "content-length") || equalsIgnoreCase(nextElement, "content-encoding"));
            moreElementsState = MoreElementsState.YES;
        }
    }
}

