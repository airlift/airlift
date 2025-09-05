package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "stream response service", description = "stream response")
public class ServiceWithStreamResponse
{
    @ApiGet(description = "streaming")
    public ApiTextStreamResponse<Food> test()
    {
        return null;
    }
}
