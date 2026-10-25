package io.airlift.http.client.testing;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.StreamingResponseHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        HttpResponseFuture<String> future = new TestingHttpClient(_ -> {
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

    @Test
    public void testStreamingResponseHandler()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        byte[] body = "0123456789abcdef".repeat(2048).getBytes(US_ASCII);
        AtomicInteger statusCode = new AtomicInteger();
        AtomicInteger chunks = new AtomicInteger();

        HttpResponseFuture<byte[]> future = new TestingHttpClient(_ -> new TestingResponse(OK, ImmutableListMultimap.of(), body))
                .executeAsync(request, new StreamingResponseHandler<byte[], RuntimeException>()
                {
                    @Override
                    public byte[] handleException(Request request, Exception exception)
                    {
                        throw propagate(request, exception);
                    }

                    @Override
                    public BodyConsumer<byte[], RuntimeException> handleHeaders(Request request, ResponseMetadata response)
                    {
                        statusCode.set(response.getStatusCode());
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        return new BodyConsumer<>()
                        {
                            @Override
                            public void accept(ByteBuffer content)
                            {
                                chunks.incrementAndGet();
                                byte[] bytes = new byte[content.remaining()];
                                content.get(bytes);
                                output.writeBytes(bytes);
                            }

                            @Override
                            public byte[] complete()
                            {
                                return output.toByteArray();
                            }
                        };
                    }
                });

        assertThat(future.get()).isEqualTo(body);
        assertThat(statusCode.get()).isEqualTo(200);
        assertThat(chunks.get()).isGreaterThan(1);
    }

    @Test
    public void testStreamingBodyConsumerFailureSkipsHandleException()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        IOException consumerFailure = new IOException("consumer failure");

        HttpResponseFuture<String> future = new TestingHttpClient(_ -> new TestingResponse(OK, ImmutableListMultimap.of(), new byte[] {1}))
                .executeAsync(request, new StreamingResponseHandler<String, IOException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        return "fallback";
                    }

                    @Override
                    public BodyConsumer<String, IOException> handleHeaders(Request request, ResponseMetadata response)
                    {
                        return new BodyConsumer<>()
                        {
                            @Override
                            public void accept(ByteBuffer content)
                                    throws IOException
                            {
                                throw consumerFailure;
                            }

                            @Override
                            public String complete()
                            {
                                return "complete";
                            }
                        };
                    }
                });

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isSameAs(consumerFailure);
    }
}
