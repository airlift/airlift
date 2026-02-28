package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

import static io.airlift.api.servertests.openapi.recursive.TestModels.TEST_POLY_TEST;
import static io.airlift.api.servertests.openapi.recursive.TestModels.TEST_SCHEMA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
@ApiService(type = ServiceType.class, name = "complex", description = "Has complex recursive poly resources")
public class ComplexRecursiveService
{
    @ApiCreate(description = "dummy", traits = ApiTrait.BETA)
    public void createLiveTable(PolyTest polyTest)
    {
        assertThat(polyTest).isEqualTo(TEST_POLY_TEST);
    }

    @ApiUpdate(description = "dummy", traits = ApiTrait.BETA)
    public void updateSchema(TransformLiveTableSchemaUpdate schemaUpdate)
    {
        assertThat(schemaUpdate).isEqualTo(TEST_SCHEMA_UPDATE);
    }
}
