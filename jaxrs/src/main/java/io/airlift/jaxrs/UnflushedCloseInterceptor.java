package io.airlift.jaxrs;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.glassfish.jersey.io.spi.FlushedCloseable;

import java.io.IOException;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * Closes Jersey's entity stream without a flush, so the server can send an entity that fits its output buffer with a Content-Length.
 */
@Provider
// innermost, so that this is the stream Jersey closes even when other interceptors wrap the entity stream
@Priority(Integer.MAX_VALUE)
public class UnflushedCloseInterceptor
        implements WriterInterceptor
{
    @Override
    public void aroundWriteTo(WriterInterceptorContext context)
            throws IOException
    {
        OutputStream entityStream = context.getOutputStream();
        // a stream installed by another interceptor may only drain its buffer on flush, so Jersey keeps flushing it
        if (entityStream.getClass().getName().startsWith("org.glassfish.jersey.")) {
            context.setOutputStream(new UnflushedCloseOutputStream(entityStream));
        }
        context.proceed();
    }

    // Jersey does not flush a FlushedCloseable before closing it
    private static final class UnflushedCloseOutputStream
            extends OutputStream
            implements FlushedCloseable
    {
        private final OutputStream delegate;

        private UnflushedCloseOutputStream(OutputStream delegate)
        {
            this.delegate = requireNonNull(delegate, "delegate is null");
        }

        @Override
        public void write(int b)
                throws IOException
        {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len)
                throws IOException
        {
            delegate.write(b, off, len);
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
}
