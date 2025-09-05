package io.airlift.api.validation;

import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithMultiPartForm
{
    @ApiUpdate(description = "get the new thing")
    public void updateThing(ApiMultiPartForm<Thing> multiPartThing)
    {
    }
}
