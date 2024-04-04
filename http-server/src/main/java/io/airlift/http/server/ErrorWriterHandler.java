package io.airlift.http.server;

import io.airlift.http.server.ErrorWriter.ErrorDetails;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Optional;

import static io.airlift.http.server.ErrorWriter.ContentType.HTML;
import static io.airlift.http.server.ErrorWriter.ContentType.JSON;
import static io.airlift.http.server.ErrorWriter.ContentType.PLAIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ErrorWriterHandler
        extends ErrorHandler
{
    private final ErrorWriter errorWriter;

    public ErrorWriterHandler(ErrorWriter errorWriter)
    {
        this.errorWriter = requireNonNull(errorWriter, "errorWriter is null");
    }

    @Override
    protected void writeErrorHtml(Request request, Writer writer, Charset charset, int code, String message, Throwable cause, boolean showStacks)
    {
        PrintWriter printWriter = (writer instanceof PrintWriter value) ? value : new PrintWriter(writer);
        errorWriter.writeError(printWriter, HTML, charset, new ErrorDetails(code, message, Optional.ofNullable(cause)));
    }

    @Override
    protected void writeErrorPlain(Request request, PrintWriter writer, int code, String message, Throwable cause, boolean showStacks)
    {
        errorWriter.writeError(writer, PLAIN, UTF_8, new ErrorDetails(code, message, Optional.ofNullable(cause)));
    }

    @Override
    protected void writeErrorJson(Request request, PrintWriter writer, int code, String message, Throwable cause, boolean showStacks)
    {
        errorWriter.writeError(writer, JSON, UTF_8, new ErrorDetails(code, message, Optional.ofNullable(cause)));
    }
}
