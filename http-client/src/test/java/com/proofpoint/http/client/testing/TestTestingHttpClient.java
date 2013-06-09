package com.proofpoint.http.client.testing;

import com.google.common.base.Function;
import com.proofpoint.http.client.AsyncHttpClient.AsyncHttpResponseFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
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
            assertSame(e.getCause(), expectedException);
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

        AsyncHttpResponseFuture<Object,RuntimeException> future = new TestingHttpClient(
                new Function<Request, Response>()
                {
                    @Override
                    public Response apply(Request input)
                    {
                        throw testingException;
                    }
                }).executeAsync(request, new DefaultExceptionResponseHandler(testingException, expectedResponse));

        assertSame(future.checkedGet(), expectedResponse);
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

    private class DefaultExceptionResponseHandler implements ResponseHandler<Object, RuntimeException>
    {
        private final RuntimeException expectedException;
        private final Object response;

        public DefaultExceptionResponseHandler(RuntimeException expectedException, Object response)
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
