package com.proofpoint.http.server;

import com.google.common.base.Preconditions;
import com.google.common.io.NullOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

class TimingFilter
        implements Filter
{
    public static final String FIRST_BYTE_TIME = TimingFilter.class.getName() + ".FIRST_BYTE_TIME";

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        TimedResponse response = new TimedResponse((HttpServletResponse) servletResponse);
        try {
            chain.doFilter(servletRequest, response);
        }
        finally {
            Long firstByteTime = response.getFirstByteTime();
            if (firstByteTime != null) {
                servletRequest.setAttribute(FIRST_BYTE_TIME, firstByteTime);
            }
        }
    }

    @Override
    public void destroy()
    {
    }

    private static class TimedResponse extends HttpServletResponseWrapper
    {
        private TimedServletOutputStream outputStream;
        private TimedPrintWriter printWriter;

        private TimedResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream()
                throws IOException
        {
            Preconditions.checkState(printWriter == null, "getWriter() has already been called");
            if (outputStream == null) {
                outputStream = new TimedServletOutputStream(super.getOutputStream());
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter()
                throws IOException
        {
            Preconditions.checkState(outputStream == null, "getOutputStream() has already been called");
            if (printWriter == null) {
                printWriter = new TimedPrintWriter(super.getWriter());
            }
            return printWriter;
        }

        public Long getFirstByteTime()
        {
            if (outputStream != null) {
                return outputStream.getFirstByteTime();
            }
            if (printWriter != null) {
                return printWriter.getFirstByteTime();
            }
            return null;
        }
    }

    private static class TimedServletOutputStream extends ServletOutputStream
    {
        private final ServletOutputStream delegate;
        private Long firstByteTime;

        private TimedServletOutputStream(ServletOutputStream delegate)
        {
            this.delegate = delegate;
        }

        public Long getFirstByteTime()
        {
            return firstByteTime;
        }

        private void recordFirstByteTime()
        {
            if (firstByteTime == null) {
                firstByteTime = System.currentTimeMillis();
            }
        }

        @Override
        public void write(int b)
                throws IOException
        {
            recordFirstByteTime();
            delegate.write(b);
        }

        @Override
        public void write(byte[] b)
                throws IOException
        {
            recordFirstByteTime();
            delegate.write(b);
        }

        @Override
        public void print(String s)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(s);
        }

        @Override
        public void write(byte[] b, int off, int len)
                throws IOException
        {
            recordFirstByteTime();
            delegate.write(b, off, len);
        }

        @Override
        public void print(boolean b)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(b);
        }

        @Override
        public void print(char c)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(c);
        }

        @Override
        public void print(int i)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(i);
        }

        @Override
        public void print(long l)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(l);
        }

        @Override
        public void print(float f)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(f);
        }

        @Override
        public void print(double d)
                throws IOException
        {
            recordFirstByteTime();
            delegate.print(d);
        }

        @Override
        public void println()
                throws IOException
        {
            recordFirstByteTime();
            delegate.println();
        }

        @Override
        public void println(String s)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(s);
        }

        @Override
        public void println(boolean b)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(b);
        }

        @Override
        public void println(char c)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(c);
        }

        @Override
        public void println(int i)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(i);
        }

        @Override
        public void println(long l)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(l);
        }

        @Override
        public void println(float f)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(f);
        }

        @Override
        public void println(double d)
                throws IOException
        {
            recordFirstByteTime();
            delegate.println(d);
        }

        @Override
        public void flush()
                throws IOException
        {
            delegate.flush();
        }

        @Override
        public void close()
                throws IOException
        {
            delegate.close();
        }
    }

    private static class TimedPrintWriter extends PrintWriter
    {
        private final PrintWriter delegate;
        private Long firstByteTime;

        private TimedPrintWriter(PrintWriter delegate)
        {
            super(new NullOutputStream());
            this.delegate = delegate;
        }

        public Long getFirstByteTime()
        {
            return firstByteTime;
        }

        private void recordFirstByteTime()
        {
            if (firstByteTime == null) {
                firstByteTime = System.currentTimeMillis();
            }
        }

        public boolean checkError()
        {
            return delegate.checkError();
        }

        @Override
        public void write(int c)
        {
            recordFirstByteTime();
            delegate.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len)
        {
            recordFirstByteTime();
            delegate.write(buf, off, len);
        }

        @Override
        public void write(char[] buf)
        {
            recordFirstByteTime();
            delegate.write(buf);
        }

        @Override
        public void write(String s, int off, int len)
        {
            recordFirstByteTime();
            delegate.write(s, off, len);
        }

        @Override
        public void write(String s)
        {
            recordFirstByteTime();
            delegate.write(s);
        }

        @Override
        public void print(boolean b)
        {
            recordFirstByteTime();
            delegate.print(b);
        }

        @Override
        public void print(char c)
        {
            recordFirstByteTime();
            delegate.print(c);
        }

        @Override
        public void print(int i)
        {
            recordFirstByteTime();
            delegate.print(i);
        }

        @Override
        public void print(long l)
        {
            recordFirstByteTime();
            delegate.print(l);
        }

        @Override
        public void print(float f)
        {
            recordFirstByteTime();
            delegate.print(f);
        }

        @Override
        public void print(double d)
        {
            recordFirstByteTime();
            delegate.print(d);
        }

        @Override
        public void print(char[] s)
        {
            recordFirstByteTime();
            delegate.print(s);
        }

        @Override
        public void print(String s)
        {
            recordFirstByteTime();
            delegate.print(s);
        }

        @Override
        public void print(Object obj)
        {
            recordFirstByteTime();
            delegate.print(obj);
        }

        @Override
        public void println()
        {
            recordFirstByteTime();
            delegate.println();
        }

        @Override
        public void println(boolean x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(char x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(int x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(long x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(float x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(double x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(char[] x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(String x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public void println(Object x)
        {
            recordFirstByteTime();
            delegate.println(x);
        }

        @Override
        public PrintWriter printf(String format, Object... args)
        {
            recordFirstByteTime();
            delegate.printf(format, args);
            return this;
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args)
        {
            recordFirstByteTime();
            delegate.printf(l, format, args);
            return this;
        }

        @Override
        public PrintWriter format(String format, Object... args)
        {
            recordFirstByteTime();
            delegate.format(format, args);
            return this;
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args)
        {
            recordFirstByteTime();
            delegate.format(l, format, args);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq)
        {
            recordFirstByteTime();
            delegate.append(csq);
            return this;
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end)
        {
            recordFirstByteTime();
            delegate.append(csq, start, end);
            return this;
        }

        @Override
        public PrintWriter append(char c)
        {
            recordFirstByteTime();
            delegate.append(c);
            return this;
        }

        @Override
        public void flush()
        {
            delegate.flush();
        }

        @Override
        public void close()
        {
            delegate.close();
        }
    }
}
