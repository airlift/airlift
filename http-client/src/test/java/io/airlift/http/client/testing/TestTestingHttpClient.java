package io.airlift.http.client.testing;

import com.google.common.base.Function;
import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.AsyncHttpClient.AsyncHttpResponseFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestTestingHttpClient
{
    @Test
    public void testExceptionFromProcessor()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        final RuntimeException expectedException = new RuntimeException("test exception");

        AsyncHttpResponseFuture<String> future = new TestingHttpClient(
                new Function<Request, Response>()
                {
                    @Override
                    public Response apply(Request input)
                    {
                        throw expectedException;
                    }
                }).executeAsync(request, new CaptureExceptionResponseHandler());

        try {
            future.get();
            fail("expected exception");
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(cause, CapturedException.class);
            assertEquals(cause.getCause(), expectedException);
        }
    }
}
