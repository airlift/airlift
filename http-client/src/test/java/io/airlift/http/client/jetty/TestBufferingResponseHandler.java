package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.Response.BytesContent;
import io.airlift.http.client.Response.Content;
import io.airlift.http.client.Response.InputStreamContent;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.StreamingResponseHandler.BodyConsumer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;

import static io.airlift.http.client.HeaderNames.CONTENT_LENGTH;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBufferingResponseHandler
{
    private static final Request REQUEST = prepareGet().setUri(URI.create("http://example.test/")).build();
    private static final int CHUNK_SIZE = 16 * 1024;

    @Test
    public void testContentLengthWithinSegmentCap()
            throws IOException
    {
        byte[] body = body(256 * 1024);
        Content content = buffer(body, Optional.of(body.length));
        assertThat(content).isInstanceOf(BytesContent.class);
        assertThat(((BytesContent) content).bytes()).isEqualTo(body);
    }

    @Test
    public void testContentLengthAboveSegmentCap()
            throws IOException
    {
        byte[] body = body(1024 * 1024 + 1);
        Content content = buffer(body, Optional.of(body.length));
        assertThat(content).isInstanceOf(InputStreamContent.class);
        assertThat(((InputStreamContent) content).inputStream().readAllBytes()).isEqualTo(body);
    }

    @Test
    public void testWithoutContentLength()
            throws IOException
    {
        byte[] body = body(1024 * 1024 + 1);
        Content content = buffer(body, Optional.empty());
        assertThat(content).isInstanceOf(InputStreamContent.class);
        assertThat(((InputStreamContent) content).inputStream().readAllBytes()).isEqualTo(body);
    }

    private static Content buffer(byte[] body, Optional<Integer> contentLength)
    {
        BufferingResponseHandler<Content, RuntimeException> handler = new BufferingResponseHandler<>(new ContentResponseHandler());
        BodyConsumer<Content, RuntimeException> consumer = handler.handleHeaders(REQUEST, new FakeResponseMetadata(contentLength));
        for (int offset = 0; offset < body.length; offset += CHUNK_SIZE) {
            consumer.accept(ByteBuffer.wrap(body, offset, Math.min(CHUNK_SIZE, body.length - offset)));
        }
        return consumer.complete();
    }

    private static byte[] body(int length)
    {
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) i;
        }
        return body;
    }

    private static class ContentResponseHandler
            implements ResponseHandler<Content, RuntimeException>
    {
        @Override
        public Content handleException(Request request, Exception exception)
        {
            throw new RuntimeException(exception);
        }

        @Override
        public Content handle(Request request, Response response)
        {
            return response.getContent();
        }
    }

    private record FakeResponseMetadata(Optional<Integer> contentLength)
            implements ResponseMetadata
    {
        @Override
        public HttpVersion getHttpVersion()
        {
            return HttpVersion.HTTP_1;
        }

        @Override
        public int getStatusCode()
        {
            return 200;
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            ImmutableListMultimap.Builder<HeaderName, String> headers = ImmutableListMultimap.builder();
            contentLength.ifPresent(length -> headers.put(CONTENT_LENGTH, String.valueOf(length)));
            return headers.build();
        }
    }
}
