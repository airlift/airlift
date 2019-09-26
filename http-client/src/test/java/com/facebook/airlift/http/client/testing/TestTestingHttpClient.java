package com.facebook.airlift.http.client.testing;

import com.facebook.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import com.facebook.airlift.http.client.AbstractHttpClientTest.CapturedException;
import com.facebook.airlift.http.client.HttpClient.HttpResponseFuture;
import com.facebook.airlift.http.client.Request;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static com.facebook.airlift.http.client.Request.Builder.prepareGet;
import static com.facebook.airlift.testing.Assertions.assertInstanceOf;
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
            assertInstanceOf(cause, CapturedException.class);
            assertEquals(cause.getCause(), expectedException);
        }
    }
}
