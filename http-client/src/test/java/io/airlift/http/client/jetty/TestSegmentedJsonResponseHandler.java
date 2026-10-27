package io.airlift.http.client.jetty;

import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Response;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.JsonResponseHandler.createSafeJsonResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.contentType;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestSegmentedJsonResponseHandler
{
    private static final JsonCodec<String> CODEC = JsonCodec.jsonCodec(String.class);

    @ParameterizedTest
    @ValueSource(ints = {32, 8192, 8193, 320 * 1024})
    public void testValidJson(int length)
    {
        String value = "x".repeat(length - 2);
        byte[] json = ("\"" + value + "\"").getBytes(UTF_8);
        JsonResponse<String> response = createFullJsonResponseHandler(CODEC).handle(null, segmentedResponse(json, true));

        assertThat(response.hasValue()).isTrue();
        assertThat(response.getValue()).isEqualTo(value);
        assertThat(response.getResponseSize()).isEqualTo(json.length);
        byte[] copy = response.getResponseBytes();
        assertThat(copy).isEqualTo(json);
        copy[0] = 0;
        assertThat(response.getJsonBytes()).isEqualTo(json);
        assertThat(response.getJson()).isEqualTo(new String(json, UTF_8));
        assertThat(response.getResponseBody()).isEqualTo(new String(json, UTF_8));

        assertThat(createJsonResponseHandler(CODEC).handle(null, segmentedResponse(json, true))).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 8192, 8193, 320 * 1024})
    public void testInvalidJson(int length)
    {
        String json = "\"" + "x".repeat(length - 1);
        JsonResponse<String> response = createFullJsonResponseHandler(CODEC).handle(null, segmentedResponse(json.getBytes(UTF_8), true));

        assertThat(response.hasValue()).isFalse();
        assertThat(response.getException()).hasMessageContaining(json);
        assertThat(response.getResponseBytes()).isEqualTo(json.getBytes(UTF_8));
        assertThat(response.getJson()).isEqualTo(json);

        assertThatThrownBy(() -> createJsonResponseHandler(CODEC).handle(null, segmentedResponse(json.getBytes(UTF_8), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(json);
    }

    @Test
    public void testSafeHandlerRedactsInvalidJson()
    {
        byte[] json = ("\"secret" + "x".repeat(320 * 1024)).getBytes(UTF_8);
        assertThatThrownBy(() -> createSafeJsonResponseHandler(CODEC, 200).handle(null, segmentedResponse(json, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to create %s from JSON response", CODEC.getType())
                .hasNoCause();
    }

    @Test
    public void testNonJsonBody()
    {
        byte[] body = "x".repeat(320 * 1024).getBytes(UTF_8);
        JsonResponse<String> response = createFullJsonResponseHandler(CODEC).handle(null, segmentedResponse(body, false));

        assertThat(response.hasValue()).isFalse();
        assertThat(response.getJsonBytes()).isNull();
        assertThat(response.getResponseBytes()).isEqualTo(body);
        assertThat(response.getResponseBody()).isEqualTo(new String(body, UTF_8));
    }

    @Test
    public void testConcurrentBodyAccess()
            throws Exception
    {
        String json = "\"" + "x".repeat(320 * 1024) + "\"";
        byte[] bytes = json.getBytes(UTF_8);
        JsonResponse<String> response = createFullJsonResponseHandler(CODEC).handle(null, segmentedResponse(bytes, true));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            tasks.add(() -> {
                assertThat(response.getResponseBytes()).isEqualTo(bytes);
                assertThat(response.getJson()).isEqualTo(json);
                assertThat(response.getResponseBody()).isEqualTo(json);
                return null;
            });
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testMaterializedResponseReleasesSegments(boolean valid)
            throws InterruptedException
    {
        ReferenceQueue<InputStream> collected = new ReferenceQueue<>();
        MaterializedResponse result = materializedResponse(valid, collected);
        try {
            System.gc();
            assertThat(collected.remove(10_000)).isSameAs(result.originalStream());
            assertThat(result.response().getResponseSize()).isGreaterThan(320 * 1024);
        }
        finally {
            Reference.reachabilityFence(result.response());
        }
    }

    private static MaterializedResponse materializedResponse(boolean valid, ReferenceQueue<InputStream> collected)
    {
        byte[] json = ("\"" + "x".repeat(320 * 1024) + (valid ? "\"" : "")).getBytes(UTF_8);
        Response original = segmentedResponse(json, true);
        WeakReference<InputStream> stream = new WeakReference<>(((Response.InputStreamContent) original.getContent()).inputStream(), collected);
        JsonResponse<String> response = createFullJsonResponseHandler(CODEC).handle(null, original);
        if (valid) {
            response.getResponseBytes();
        }
        return new MaterializedResponse(response, stream);
    }

    private record MaterializedResponse(JsonResponse<String> response, WeakReference<InputStream> originalStream) {}

    private static Response segmentedResponse(byte[] bytes, boolean json)
    {
        List<byte[]> segments = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += 4096) {
            byte[] segment = new byte[4096];
            System.arraycopy(bytes, offset, segment, 0, Math.min(segment.length, bytes.length - offset));
            segments.add(segment);
        }
        return new JettyResponse(
                new TestingResponse(OK, contentType(json ? JSON_UTF_8 : PLAIN_TEXT_UTF_8), bytes),
                new ResponseBody(segments, bytes.length));
    }
}
