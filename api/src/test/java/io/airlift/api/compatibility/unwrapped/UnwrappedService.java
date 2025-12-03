package io.airlift.api.compatibility.unwrapped;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.compatibility.DummyServiceType;

@ApiService(name = "dummy", description = "A dummy service for testing", type = DummyServiceType.class)
public class UnwrappedService
{
    @ApiCreate(description = "make it")
    public void makeIt(Unwrapped ignore)
    {
        // do nothing
    }
}
