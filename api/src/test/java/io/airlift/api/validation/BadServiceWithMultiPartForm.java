package io.airlift.api.validation;

import io.airlift.api.ApiList;
import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class BadServiceWithMultiPartForm
{
    @ApiList(description = "get the new thing")
    public ApiMultiPartForm<Thing> badMultiPart()
    {
        return null;
    }
}
