package io.airlift.http.client.testing;

import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TestTestingHttpClient
{
    @Test
    public void testExceptionFromProcessor()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        RuntimeException expectedException = new RuntimeException("test exception");

        try (HttpClient client = new TestingHttpClient(_ -> { throw expectedException; })) {
            HttpResponseFuture<String> future = execute(client, request, new CaptureExceptionResponseHandler());

            try {
                future.get();
                fail("expected exception");
            }
            catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertThat(cause).isInstanceOf(CapturedException.class);
                assertThat(cause.getCause()).isEqualTo(expectedException);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static <T, E> HttpResponseFuture<T> execute(HttpClient client, Request request, ResponseHandler<T, ?> responseHandler)
    {
        return client.executeAsync(request, responseHandler);
    }
}
