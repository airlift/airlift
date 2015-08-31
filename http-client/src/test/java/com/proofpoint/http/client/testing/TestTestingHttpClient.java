package com.proofpoint.http.client.testing;

import com.google.common.base.Function;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.testing.TestingHttpClient.Processor;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestTestingHttpClient
{
    @Test
    public void testBlocking()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST);

        String result = new TestingHttpClient(
                (Function<Request, Response>) input -> {
                    assertSame(input, request);
                    return expectedResponse;
                })
                .execute(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(result, "expected response");
    }

    @Test
    public void testAsync()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST);

        HttpResponseFuture<String> future = new TestingHttpClient(
                (Function<Request, Response>) input -> {
                    assertSame(input, request);
                    return expectedResponse;
                })
                .executeAsync(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(future.get(), "expected response");
    }

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

    @Test
    public void testReplaceProcessor()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST);

        TestingHttpClient client = new TestingHttpClient((Processor) input -> {
            throw new UnsupportedOperationException();
        });

        client.setProcessor(input -> {
            assertSame(input, request);
            return expectedResponse;
        });

        HttpResponseFuture<String> future = client
                .executeAsync(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(future.get(), "expected response");
    }

    @Test
    public void testDefaultConstructor()
    {
        TestingHttpClient client = new TestingHttpClient();
        try {
            client.execute(prepareGet().setUri(URI.create("/")).build(), createStatusResponseHandler());
            fail("Expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException ignored) {
        }
    }

    private static class CaptureExceptionResponseHandler implements ResponseHandler<String, CapturedException>
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
