package com.proofpoint.http.client.testing;

import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.http.client.InputStreamBodySource;
import com.proofpoint.http.client.StaticBodyGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class BodySourceTester
{
    private BodySourceTester()
    {
    }

    @SuppressWarnings("deprecation")
    public static void writeBodySourceTo(BodySource bodySource, final OutputStream out)
            throws Exception
    {
        if (bodySource instanceof StaticBodyGenerator) {
            out.write(((StaticBodyGenerator) bodySource).getBody());
        }
        else if (bodySource instanceof BodyGenerator) {
            ((BodyGenerator) bodySource).write(out);
        }
        else if (bodySource instanceof InputStreamBodySource) {
            InputStream in = ((InputStreamBodySource) bodySource).getInputStream();
            byte[] buf = new byte[((InputStreamBodySource) bodySource).getBufferSize()];
            while (true) {
                int r = in.read(buf);
                if (r == -1) {
                    break;
                }
                out.write(buf, 0, r);
            }
        }
        else if (bodySource instanceof DynamicBodySource) {
            final AtomicBoolean closed = new AtomicBoolean(false);
            Writer writer = ((DynamicBodySource) bodySource).start(new OutputStream()
            {
                @Override
                public void write(int b)
                        throws IOException
                {
                    out.write(b);
                }

                @Override
                public void write(byte[] b)
                        throws IOException
                {
                    out.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len)
                        throws IOException
                {
                    out.write(b, off, len);
                }

                @Override
                public void flush()
                        throws IOException
                {
                    out.flush();
                }

                @Override
                public void close()
                {
                    closed.set(true);
                }
            });

            while (!closed.get()) {
                writer.write();
            }

            if (writer instanceof AutoCloseable) {
                ((AutoCloseable) writer).close();
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported BodySource type");
        }
    }
}
