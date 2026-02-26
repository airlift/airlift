package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(type = ServiceType.class, name = "complex", description = "Has complex recursive poly resources")
public class ComplexRecursiveService
{
    @ApiCreate(description = "dummy", traits = ApiTrait.BETA)
    public void createLiveTable(PolyTest polyTest)
    {
        // NOP
    }

    @ApiUpdate(description = "dummy", traits = ApiTrait.BETA)
    public void updateSchema(TransformLiveTableSchemaUpdate schemaUpdate)
    {
        // NOP
    }
}
