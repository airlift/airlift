package io.airlift.http.client.testing;

import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.Request;
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

        HttpResponseFuture<String> future = new TestingHttpClient(input -> {
            throw expectedException;
        }).executeAsync(request, new CaptureExceptionResponseHandler());

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
