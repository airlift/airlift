package io.airlift.api.servertests.quota;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "quota", description = "Does quota things")
public class BadQuotaService
{
    @ApiCreate(description = "dummy")
    public void missing()
    {
    }
}
