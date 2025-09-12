package io.airlift.api.validation;

import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "stream response service", description = "stream response")
public class BadServiceWithStreamResponse2
{
    @SuppressWarnings("unused")
    @ApiUpdate(description = "streaming")
    public void test(ApiTextStreamResponse<Food> dummy)
    {
    }
}
