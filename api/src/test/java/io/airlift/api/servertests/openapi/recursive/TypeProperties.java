package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
@ApiPolyResource(key = "typeKey", name = "typeProperties", description = "dummy")
public sealed interface TypeProperties
{
    @ApiResource(name = "ingestProperties", description = "dummy")
    record IngestProperties(
            @ApiDescription("dummy") String topicName,
            @ApiDescription("dummy") StartStrategy startStrategy,
            @ApiDescription("dummy") Long partitionMaxBytesPerSec)
            implements TypeProperties
    {
    }

    @ApiResource(name = "csvParameters", description = "dummy")
    record CsvParameters(
            @ApiDescription("dummy") CsvDelimiter delimiter,
            @ApiDescription("dummy") CsvQuoteChar quoteChar,
            @ApiDescription("dummy") CsvEscapeChar escapeChar,
            @ApiDescription("dummy") int numHeaderLinesToExclude)
    {
    }

    @ApiResource(name = "fileIngestProperties", description = "dummy")
    record FileIngestProperties(
            @ApiDescription("dummy") Optional<String> pathSuffix,
            @ApiDescription("dummy") FileFormat fileFormat,
            @ApiDescription("dummy") int pollingIntervalInMinutes,
            @ApiDescription("dummy") Optional<CsvParameters> csvParameters)
            implements TypeProperties
    {
    }

    @ApiResource(name = "ingestJsonTransformProperties", description = "dummy")
    record IngestJsonTransformProperties(
            @ApiDescription("dummy") Optional<List<PartitionColumn>> userDefinedPartitionSpec,
            @ApiDescription("dummy") Optional<String> sortColumnName)
            implements TypeProperties
    {
    }

    @ApiResource(name = "ingestSchemaRegistryTransformProperties", description = "dummy")
    record IngestSchemaRegistryTransformProperties(
            @ApiDescription("dummy") String schemaRegistryId,
            @ApiDescription("dummy") String subjectName,
            @ApiDescription("dummy") Optional<List<TypeProperties.PartitionColumn>> userDefinedPartitionSpec,
            @ApiDescription("dummy") Optional<String> sortColumnName)
            implements TypeProperties
    {}

    enum StartStrategy
    {
        Earliest,
        Latest
    }

    enum FileFormat
    {
        Json,
        Csv
    }

    enum CsvDelimiter
    {
        Comma,
        Tab,
        Pipe,
        Semicolon
    }

    enum CsvQuoteChar
    {
        DoubleQuote,
        SingleQuote
    }

    enum CsvEscapeChar
    {
        DoubleQuote,
        Backslash
    }

    @ApiResource(name = "partitionColumn", description = "dummy")
    record PartitionColumn(
            @ApiDescription("dummy") String trinoColumnName,
            @ApiDescription("dummy") String transformType,
            @ApiDescription("dummy") Optional<Integer> bucketCount,
            @ApiDescription("dummy") Optional<Integer> truncateWidth)
    {
    }
}
