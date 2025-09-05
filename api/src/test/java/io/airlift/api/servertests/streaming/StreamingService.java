package io.airlift.api.servertests.streaming;

import io.airlift.api.ApiCustom;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiByteStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiOutputStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ServiceType;
import io.airlift.api.responses.ApiException;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static io.airlift.api.ApiType.GET;
import static io.airlift.api.ApiType.LIST;

@ApiService(type = ServiceType.class, name = "streaming", description = "Does streaming things")
public class StreamingService
{
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

    @ApiCustom(type = LIST, verb = "bad", description = "bad")
    public List<StreamingResource> bad()
    {
        throw ApiException.unauthorized("bad");
    }
}
