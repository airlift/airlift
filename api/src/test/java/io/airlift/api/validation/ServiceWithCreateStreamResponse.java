package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "create stream response service", description = "stream response")
public class ServiceWithCreateStreamResponse
{
    @ApiCreate(description = "streaming", quotas = "something")
    public ApiTextStreamResponse<Food> test()
    {
        return null;
    }
}
