package io.airlift.http.server;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface ErrorWriter
{
    void writeError(PrintWriter writer, ContentType contentType, Charset charset, ErrorDetails error);

    enum ContentType
    {
        JSON,
        HTML,
        PLAIN;
    }

    record ErrorDetails(int responseCode, String message, Optional<Throwable> cause)
    {
        public ErrorDetails {
            requireNonNull(message, "message is null");
            requireNonNull(cause, "cause is null");
        }
    }
}
