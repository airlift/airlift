package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApiResource(name = "ingestColumn", description = "dummy")
public record JsonIngestColumn(
        @ApiDescription("dummy") int columnId,
        @ApiDescription("dummy") String trinoColumnName,
        @ApiDescription("dummy") JsonParserConfig fieldParserConfig,
        @ApiDescription("dummy") Type trinoType,
        @ApiDescription("dummy") List<JsonIngestColumn> nestedColumns)
{
    @ApiResource(name = "type", description = "dummy")
    public record Type(
            @ApiDescription("dummy") TypeId typeId,
            @ApiDescription("dummy") Optional<Map<String, String>> typeParameters)
    {
        public enum TypeId
        {
            Boolean,
            Integer,
            Bigint,
            Real,
            Double,
            Decimal,
            Date,
            Time,
            Timestamp,
            TimestampTz,
            Varchar,
            Uuid,
            Varbinary,
            Row,
            Array,
            Map
        }
    }

    @ApiResource(name = "jsonParserConfig", description = "dummy")
    public record JsonParserConfig(
            @ApiDescription("dummy") String pointer,
            @ApiDescription("dummy") Optional<Map<String, String>> parseParameters)
    {
    }
}
