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
import io.airlift.api.ApiResourceVersion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

@ApiResource(name = "transformLiveTableSchemaUpdate", description = "dummy")
public record TransformLiveTableSchemaUpdate(
        ApiResourceVersion syncToken,
        @ApiDescription("dummy") TableId transformLiveTableSchemaUpdateId,
        @ApiDescription("dummy") NewSchema newSchema)
{
    @ApiPolyResource(key = "typeKey", name = "newSchema", description = "dummy")
    public sealed interface NewSchema
    {
        @ApiResource(name = "newJsonSchema", description = "New schema for a JSON transform live table")
        record NewJsonSchema(
                @ApiDescription("dummy") List<UpdatedJsonIngestColumn> columns,
                @ApiDescription("dummy") Optional<String> sortColumnName)
                implements NewSchema
        {
        }
    }

    private static <I, O> List<O> listTransform(Collection<? extends I> list, Function<I, O> transformFunction)
    {
        return listTransform(list.stream(), transformFunction);
    }

    private static <I, O> List<O> listTransform(Stream<? extends I> stream, Function<I, O> transformFunction)
    {
        return stream.map(transformFunction)
                .collect(toImmutableList());
    }
}
