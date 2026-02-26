/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

@ApiPolyResource(key = "typeKey", name = "updatedJsonIngestColumn", description = "dummy")
public sealed interface UpdatedJsonIngestColumn
{
    @ApiResource(name = "newJsonIngestColumnInSchemaUpdate", description = "dummy")
    record NewJsonIngestColumn(
            @ApiDescription("dummy") String trinoColumnName,
            @ApiDescription("dummy") JsonIngestColumn.JsonParserConfig fieldParserConfig,
            @ApiDescription("dummy") JsonIngestColumn.Type trinoType,
            @ApiDescription("dummy") List<NewJsonIngestColumn> nestedColumns)
            implements UpdatedJsonIngestColumn
    {
    }

    @ApiResource(name = "existingJsonIngestColumn", description = "dummy")
    record ExistingJsonIngestColumn(
            @ApiDescription("dummy") int columnId,
            @ApiDescription("dummy") String trinoColumnName,
            @ApiDescription("dummy") JsonIngestColumn.JsonParserConfig fieldParserConfig,
            @ApiDescription("dummy") JsonIngestColumn.Type trinoType,
            @ApiDescription("dummy") Optional<List<UpdatedJsonIngestColumn>> nestedColumns)
            implements UpdatedJsonIngestColumn
    {
        public ExistingJsonIngestColumn(JsonIngestColumn jsonIngestColumn)
        {
            this(
                    jsonIngestColumn.columnId(),
                    jsonIngestColumn.trinoColumnName(),
                    jsonIngestColumn.fieldParserConfig(),
                    jsonIngestColumn.trinoType(),
                    Optional.of(listTransform(jsonIngestColumn.nestedColumns(), ExistingJsonIngestColumn::new)));
        }

        private static <I, O> List<O> listTransform(Collection<? extends I> list, Function<I, O> transformFunction)
        {
            return listTransform(list.stream(), transformFunction);
        }

        private static <I, O> List<O> listTransform(Stream<? extends I> stream, Function<I, O> transformFunction)
        {
            return stream.map(transformFunction).collect(toImmutableList());
        }
    }
}
