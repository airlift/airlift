package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
@ApiResource(name = "poly", description = "dummy")
public record PolyTest(
        @ApiDescription("dummy") Optional<String> description,
        @ApiDescription("dummy") String schemaName,
        @ApiDescription("dummy") String tableName,
        @ApiDescription("dummy") Optional<Long> dataRetentionDays,
        @ApiDescription("dummy") NewTypeProperties typeProperties)
{
    @ApiPolyResource(key = "typeKey", name = "newTypeProperties", description = "dummy")
    public sealed interface NewTypeProperties
    {
        @ApiResource(name = "newIngestProperties", description = "dummy")
        record NewIngestProperties(
                @ApiDescription("dummy") String topicName,
                @ApiDescription("dummy") TypeProperties.StartStrategy startStrategy,
                @ApiDescription("dummy") Long partitionMaxBytesPerSec,
                @ApiDescription("dummy") Long flushIntervalSeconds)
                implements NewTypeProperties
        {
        }

        @ApiResource(name = "newFileIngestProperties", description = "dummy")
        record NewFileIngestProperties(
                @ApiDescription("dummy") Optional<String> pathSuffix,
                @ApiDescription("dummy") TypeProperties.FileFormat fileFormat,
                @ApiDescription("dummy") int pollingIntervalInMinutes,
                @ApiDescription("dummy") Optional<TypeProperties.CsvParameters> csvParameters)
                implements NewTypeProperties
        {
        }

        @ApiResource(name = "newIngestJsonTransformProperties", description = "dummy")
        record NewIngestJsonTransformProperties(
                @ApiDescription("dummy") List<NewJsonIngestColumn> userDefinedSchema,
                @ApiDescription("dummy")
                Optional<List<TypeProperties.IngestJsonTransformProperties.PartitionColumn>> userDefinedPartitionSpec,
                @ApiDescription("dummy") Optional<String> sortColumnName)
                implements NewTypeProperties
        {
        }

        @ApiResource(name = "newIngestSchemaRegistryTransformProperties", description = "dummy")
        record NewIngestSchemaRegistryTransformProperties(
                @ApiDescription("dummy") String schemaRegistryId,
                @ApiDescription("dummy") String subjectName,
                @ApiDescription("dummy")
                Optional<List<TypeProperties.IngestJsonTransformProperties.PartitionColumn>> userDefinedPartitionSpec,
                @ApiDescription("dummy") Optional<String> sortColumnName)
                implements NewTypeProperties
        {
        }

        @ApiResource(name = "newJsonIngestColumn", description = "dummy")
        record NewJsonIngestColumn(
                @ApiDescription("dummy") String trinoColumnName,
                @ApiDescription("dummy") JsonIngestColumn.JsonParserConfig fieldParserConfig,
                @ApiDescription("dummy") JsonIngestColumn.Type trinoType,
                @ApiDescription("dummy") Optional<List<NewJsonIngestColumn>> nestedColumns)
                implements NewTypeProperties
        {
        }
    }
}
