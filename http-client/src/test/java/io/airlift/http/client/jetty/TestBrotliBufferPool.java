package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import org.eclipse.jetty.compression.brotli.BrotliCompression;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.Content;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static io.airlift.http.client.jetty.JettyHttpClient.maxPooledBufferSize;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBrotliBufferPool
{
    @Test
    public void testDecodedOutputBuffersAreReused()
            throws Exception
    {
        byte[] body = "x".repeat(4 * 1024 * 1024).getBytes(UTF_8);
        ArrayByteBufferPool pool = new ArrayByteBufferPool.Quadratic(
                0,
                maxPooledBufferSize(new HttpClientConfig().setBrotliCompressionEnabled(true)),
                Integer.MAX_VALUE);
        pool.setStatisticsEnabled(true);
        BrotliCompression compression = new BrotliCompression();
        compression.setByteBufferPool(pool);

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OutputStream output = compression.newEncoderOutputStream(encoded)) {
            output.write(body);
        }

        try {
            for (int i = 0; i < 3; i++) {
                try (InputStream input = Content.Source.asInputStream(compression.newDecoderSource(Content.Source.from(ByteBuffer.wrap(encoded.toByteArray()))))) {
                    assertThat(input.readAllBytes()).isEqualTo(body);
                }
            }
            assertThat(pool.getNoBucketDirectAcquires()).isEmpty();
            assertThat(pool.getDirectByteBufferCount()).isEqualTo(1);
        }
        finally {
            pool.clear();
        }
    }
}
