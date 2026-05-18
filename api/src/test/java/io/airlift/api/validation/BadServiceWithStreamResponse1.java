package io.airlift.api.validation;

import io.airlift.api.ApiDelete;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "stream response service", description = "stream response")
public class BadServiceWithStreamResponse1
{
    @ApiDelete(description = "streaming")
    public ApiTextStreamResponse<Food> test()
    {
        return null;
    }
}
