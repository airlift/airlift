package io.airlift.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public sealed interface ApiStreamResponse
{
    @SuppressWarnings("unused")
    record ApiTextStreamResponse<RESOURCE>(Reader stream)
            implements ApiStreamResponse
    {
        public ApiTextStreamResponse
        {
            requireNonNull(stream, "stream is null");
        }

        public ApiTextStreamResponse(String content)
        {
            this(Reader.of(content));
        }
    }

    @SuppressWarnings("unused")
    record ApiByteStreamResponse<RESOURCE>(InputStream stream)
            implements ApiStreamResponse
    {
        public ApiByteStreamResponse
        {
            requireNonNull(stream, "stream is null");
        }

        public ApiByteStreamResponse(byte[] content)
        {
            this(new ByteArrayInputStream(content));
        }
    }

    @SuppressWarnings("unused")
    record ApiOutputStreamResponse<RESOURCE>(Consumer<OutputStream> stream)
            implements ApiStreamResponse
    {
        public ApiOutputStreamResponse
        {
            requireNonNull(stream, "stream is null");
        }
    }
}
