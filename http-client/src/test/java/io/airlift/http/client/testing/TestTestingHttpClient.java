package io.airlift.http.client.testing;

import com.google.common.base.Function;
import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.AsyncHttpClient.AsyncHttpResponseFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
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

        AsyncHttpResponseFuture<String,CapturedException> future = new TestingHttpClient(
                new Function<Request, Response>()
                {
                    @Override
                    public Response apply(Request input)
                    {
                        throw expectedException;
                    }
                }).executeAsync(request, new CaptureExceptionResponseHandler());

        try {
            future.checkedGet();
            fail("expected exception");
        }
        catch (CapturedException e) {
            assertEquals(e.getCause(), expectedException);
        }
    }
}
