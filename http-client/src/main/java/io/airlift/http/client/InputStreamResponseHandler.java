package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.util.Objects.requireNonNull;

/**
 * NOTE: intended for sync client execution. Cannot be used with {@link HttpClient#executeAsync(Request, ResponseHandler)}
 */
public class InputStreamResponseHandler
        implements ResponseHandler<InputStreamResponseHandler.InputStreamResponse, RuntimeException>
{
    public static InputStreamResponseHandler inputStreamResponseHandler()
    {
        return new InputStreamResponseHandler();
    }

    private InputStreamResponseHandler() {}

    public static final class InputStreamResponse
            implements AutoCloseable
    {
        private final Response response;
        private final Response.CompletionCallback callback;
        private final ListMultimap<HeaderName, String> headers;

        private InputStreamResponse(Response response)
        {
            this.response = requireNonNull(response, "response is null");
            this.headers = ImmutableListMultimap.copyOf(response.getHeaders());
            // Replace response callback with a noop, so we can complete it later (thus leaving inputStream still open)
            this.callback = response.getCompletionCallback();
            response.setCompletionCallback(ignored -> {});
        }

        public void write(OutputStream output)
                throws IOException
        {
            response.getInputStream().transferTo(output);
            output.flush();
        }

        @Override
        public void close()
        {
            callback.onComplete(response);
        }

        public int statusCode()
        {
            return response.getStatusCode();
        }

        public Optional<String> header(String name)
        {
            return Optional.of(headers(name)).flatMap(values -> {
                if (values.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(values.getFirst());
            });
        }

        public List<String> headers(String name)
        {
            return headers.get(HeaderName.of(name));
        }

        public ListMultimap<HeaderName, String> headers()
        {
            return headers;
        }
    }

    @Override
    public InputStreamResponse handleException(Request request, Exception exception)
            throws RuntimeException
    {
        throw propagate(request, exception);
    }

    @Override
    public InputStreamResponse handle(Request request, Response response)
            throws RuntimeException
    {
        return new InputStreamResponse(response);
    }
}
