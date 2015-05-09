package com.proofpoint.http.client.testing;

import com.google.common.base.Function;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
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

        HttpResponseFuture<String> future = new TestingHttpClient(
                (Function<Request, Response>) input -> {
                    throw expectedException;
                }).executeAsync(request, new CaptureExceptionResponseHandler());

        try {
            future.get();
            fail("expected exception");
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(cause, CapturedException.class);
            assertSame(cause.getCause(), expectedException);
        }
    }

    @Test
    public void testExceptionFromProcessorWithDefaultingHandler()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        final RuntimeException testingException = new RuntimeException("test exception");
        final Object expectedResponse = new Object();

        HttpResponseFuture<Object> future = new TestingHttpClient(
                (Function<Request, Response>) input -> {
                    throw testingException;
                }).executeAsync(request, new DefaultExceptionResponseHandler(testingException, expectedResponse));

        assertSame(future.get(), expectedResponse);
    }

    public static class CaptureExceptionResponseHandler implements ResponseHandler<String, CapturedException>
    {
        @Override
        public String handleException(Request request, Exception exception)
                throws CapturedException
        {
            throw new CapturedException(exception);
        }

        @Override
        public String handle(Request request, Response response)
                throws CapturedException
        {
            throw new UnsupportedOperationException();
        }

    }

    protected static class CapturedException extends Exception
    {
        public CapturedException(Exception exception)
        {
            super(exception);
        }
    }

    private static class DefaultExceptionResponseHandler implements ResponseHandler<Object, RuntimeException>
    {
        private final RuntimeException expectedException;
        private final Object response;

        DefaultExceptionResponseHandler(RuntimeException expectedException, Object response)
        {
            this.expectedException = expectedException;
            this.response = response;
        }

        @Override
        public Object handleException(Request request, Exception exception)
        {
            assertEquals(exception, expectedException);
            return response;
        }

        @Override
        public Object handle(Request request, Response response)
        {
            throw new UnsupportedOperationException();
        }
    }
}
