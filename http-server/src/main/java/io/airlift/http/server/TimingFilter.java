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
package io.airlift.http.server;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.ee10.servlet.util.ServletOutputStreamWrapper;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.NanoTime;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

class TimingFilter
        implements Filter
{
    @VisibleForTesting
    static final String FIRST_BYTE_TIME = TimingFilter.class.getName() + ".FIRST_BYTE_TIME";

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        TimedResponse response = new TimedResponse((HttpServletResponse) servletResponse);
        servletRequest.setAttribute(FIRST_BYTE_TIME, response.getFirstByteTime());
        chain.doFilter(servletRequest, response);
    }

    public static Long getFirstByteTime(Request request)
    {
        if (request.getAttribute(FIRST_BYTE_TIME) instanceof AtomicLong atomicLong) {
            return atomicLong.get();
        }
        return null;
    }

    @Override
    public void destroy()
    {
    }

    private static class TimedResponse
            extends HttpServletResponseWrapper
    {
        private final AtomicLong firstByteTime = new AtomicLong();

        private TimedResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream()
                throws IOException
        {
            return new TimedServletOutputStream(super.getOutputStream(), firstByteTime);
        }

        @Override
        public PrintWriter getWriter()
                throws IOException
        {
            return new TimedPrintWriter(super.getWriter(), firstByteTime);
        }

        public AtomicLong getFirstByteTime()
        {
            return firstByteTime;
        }
    }

    private static class TimedServletOutputStream
            extends ServletOutputStreamWrapper
    {
        private final AtomicLong firstByteTime;

        private TimedServletOutputStream(ServletOutputStream delegate, AtomicLong firstByteTime)
        {
            super(delegate);
            this.firstByteTime = requireNonNull(firstByteTime, "firstByteTime is null");
        }

        private void recordFirstByteTime()
        {
            firstByteTime.compareAndSet(0, NanoTime.now());
        }

        @Override
        public void write(int b)
                throws IOException
        {
            recordFirstByteTime();
            super.write(b);
        }

        @Override
        public void write(byte[] b)
                throws IOException
        {
            recordFirstByteTime();
            super.write(b);
        }

        @Override
        public void print(String s)
                throws IOException
        {
            recordFirstByteTime();
            super.print(s);
        }

        @Override
        public void write(byte[] b, int off, int len)
                throws IOException
        {
            recordFirstByteTime();
            super.write(b, off, len);
        }

        @Override
        public void print(boolean b)
                throws IOException
        {
            recordFirstByteTime();
            super.print(b);
        }

        @Override
        public void print(char c)
                throws IOException
        {
            recordFirstByteTime();
            super.print(c);
        }

        @Override
        public void print(int i)
                throws IOException
        {
            recordFirstByteTime();
            super.print(i);
        }

        @Override
        public void print(long l)
                throws IOException
        {
            recordFirstByteTime();
            super.print(l);
        }

        @Override
        public void print(float f)
                throws IOException
        {
            recordFirstByteTime();
            super.print(f);
        }

        @Override
        public void print(double d)
                throws IOException
        {
            recordFirstByteTime();
            super.print(d);
        }

        @Override
        public void println()
                throws IOException
        {
            recordFirstByteTime();
            super.println();
        }

        @Override
        public void println(String s)
                throws IOException
        {
            recordFirstByteTime();
            super.println(s);
        }

        @Override
        public void println(boolean b)
                throws IOException
        {
            recordFirstByteTime();
            super.println(b);
        }

        @Override
        public void println(char c)
                throws IOException
        {
            recordFirstByteTime();
            super.println(c);
        }

        @Override
        public void println(int i)
                throws IOException
        {
            recordFirstByteTime();
            super.println(i);
        }

        @Override
        public void println(long l)
                throws IOException
        {
            recordFirstByteTime();
            super.println(l);
        }

        @Override
        public void println(float f)
                throws IOException
        {
            recordFirstByteTime();
            super.println(f);
        }

        @Override
        public void println(double d)
                throws IOException
        {
            recordFirstByteTime();
            super.println(d);
        }
    }

    private static class TimedPrintWriter
            extends PrintWriter
    {
        private final AtomicLong firstByteTime;

        private TimedPrintWriter(PrintWriter delegate, AtomicLong firstByteTime)
        {
            super(delegate);
            this.firstByteTime = requireNonNull(firstByteTime, "firstByteTime is null");
        }

        private void recordFirstByteTime()
        {
            firstByteTime.compareAndSet(0, NanoTime.now());
        }

        @Override
        public void write(int c)
        {
            recordFirstByteTime();
            super.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len)
        {
            recordFirstByteTime();
            super.write(buf, off, len);
        }

        @Override
        public void write(char[] buf)
        {
            recordFirstByteTime();
            super.write(buf);
        }

        @Override
        public void write(String s, int off, int len)
        {
            recordFirstByteTime();
            super.write(s, off, len);
        }

        @Override
        public void write(String s)
        {
            recordFirstByteTime();
            super.write(s);
        }

        @Override
        public void print(boolean b)
        {
            recordFirstByteTime();
            super.print(b);
        }

        @Override
        public void print(char c)
        {
            recordFirstByteTime();
            super.print(c);
        }

        @Override
        public void print(int i)
        {
            recordFirstByteTime();
            super.print(i);
        }

        @Override
        public void print(long l)
        {
            recordFirstByteTime();
            super.print(l);
        }

        @Override
        public void print(float f)
        {
            recordFirstByteTime();
            super.print(f);
        }

        @Override
        public void print(double d)
        {
            recordFirstByteTime();
            super.print(d);
        }

        @Override
        public void print(char[] s)
        {
            recordFirstByteTime();
            super.print(s);
        }

        @Override
        public void print(String s)
        {
            recordFirstByteTime();
            super.print(s);
        }

        @Override
        public void print(Object obj)
        {
            recordFirstByteTime();
            super.print(obj);
        }

        @Override
        public void println()
        {
            recordFirstByteTime();
            super.println();
        }

        @Override
        public void println(boolean x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(char x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(int x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(long x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(float x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(double x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(char[] x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(String x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public void println(Object x)
        {
            recordFirstByteTime();
            super.println(x);
        }

        @Override
        public PrintWriter printf(String format, Object... args)
        {
            recordFirstByteTime();
            super.printf(format, args);
            return this;
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args)
        {
            recordFirstByteTime();
            super.printf(l, format, args);
            return this;
        }

        @Override
        public PrintWriter format(String format, Object... args)
        {
            recordFirstByteTime();
            super.format(format, args);
            return this;
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args)
        {
            recordFirstByteTime();
            super.format(l, format, args);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq)
        {
            recordFirstByteTime();
            super.append(csq);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end)
        {
            recordFirstByteTime();
            super.append(csq, start, end);
            return this;
        }

        @Override
        public PrintWriter append(char c)
        {
            recordFirstByteTime();
            super.append(c);
            return this;
        }
    }
}
