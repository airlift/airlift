package io.airlift.mcp.internal;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.server.ChunkedOutput;

import java.io.IOException;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/*
    Jersey buffers output until it exceeds a max, and then it switches
    to chunked output. However, their ChunkedOutput requires threading.
    StreamingOutput is much better. This class looks to Jersey like
    it's both a StreamingOutput and a ChunkedOutput so you get
    the nice StreamingOutput behavior without the buffering.
 */
class BufferDefeatingStreamingOutput
        extends ChunkedOutput<String>
        implements StreamingOutput
{
    private final StreamingOutput delegate;

    BufferDefeatingStreamingOutput(StreamingOutput delegate)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
    }

    @Override
    public void write(String chunk)
    {
        throw new UnsupportedOperationException("Use StreamingOutput.write(OutputStream) instead");
    }

    @Override
    public void write(OutputStream output)
            throws IOException, WebApplicationException
    {
        try {
            delegate.write(output);
        }
        finally {
            // if we don't close the chunked output stream, Jersey will hang
            close();
        }
    }

    @Override
    protected void flushQueue()
    {
        // do nothing
    }
}
