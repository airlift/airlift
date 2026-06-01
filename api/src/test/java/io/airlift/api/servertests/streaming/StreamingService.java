package io.airlift.api.servertests.streaming;

import com.google.inject.Inject;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiByteStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiOutputStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiServerSentEventStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ServiceType;
import io.airlift.api.responses.ApiException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static io.airlift.api.ApiType.CREATE;
import static io.airlift.api.ApiType.GET;
import static io.airlift.api.ApiType.LIST;
import static java.util.Objects.requireNonNull;

@ApiService(type = ServiceType.class, name = "streaming", description = "Does streaming things")
public class StreamingService
{
    private final ApiQuotaController quotaController;

    @Inject
    public StreamingService(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @ApiGet(description = "streaming")
    public ApiByteStreamResponse<StreamingResource> streamBytes()
    {
        return new ApiByteStreamResponse<>("This is streaming bytes".getBytes(StandardCharsets.UTF_8));
    }

    @ApiCustom(type = GET, verb = "chars", description = "yep")
    public ApiTextStreamResponse<StreamingResource> streamChars()
    {
        return new ApiTextStreamResponse<>("This is streaming chars");
    }

    @ApiCustom(type = GET, verb = "output", description = "yep")
    public ApiOutputStreamResponse<StreamingResource> streamOutput(@ApiParameter ApiResponseHeaders responseHeaders)
    {
        Consumer<OutputStream> consumer = outputStream -> {
            try {
                outputStream.write("This is streaming output".getBytes(StandardCharsets.UTF_8));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseHeaders.headers().put("Content-Disposition", "attachment; filename=\"foo.bar\"");
        return new ApiOutputStreamResponse<>(consumer);
    }

    @ApiCustom(type = CREATE, verb = "post", description = "yep", quotas = "STREAMING")
    public ApiTextStreamResponse<StreamingResource> streamPost(@Context Request request, StreamingRequest streamingRequest)
    {
        quotaController.recordQuotaUsage(request, "STREAMING");
        return new ApiTextStreamResponse<>("This is streaming post: " + streamingRequest.something());
    }

    @ApiCustom(type = GET, verb = "events", description = "server-sent events")
    public ApiServerSentEventStreamResponse<StreamingResource, StreamingEvent> streamEvents()
    {
        return new ApiServerSentEventStreamResponse<>(outputStream -> {
            try {
                outputStream.write("data: {\"type\":\"message\",\"message\":\"hello\"}\n\n".getBytes(StandardCharsets.UTF_8));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ApiCustom(type = CREATE, verb = "postEvents", description = "server-sent events over post", quotas = "postEvents")
    public ApiServerSentEventStreamResponse<StreamingResource, StreamingEvent> createEvents(@Context Request request)
    {
        quotaController.recordQuotaUsage(request, "postEvents");
        return new ApiServerSentEventStreamResponse<>(outputStream -> {
            try {
                outputStream.write("data: {\"type\":\"message\",\"message\":\"posted\"}\n\n".getBytes(StandardCharsets.UTF_8));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ApiCustom(type = LIST, verb = "bad", description = "bad")
    public List<StreamingResource> bad()
    {
        throw ApiException.unauthorized("bad");
    }
}
