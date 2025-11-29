package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiStringId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApiResource(name = "allOfIt", description = "All types")
public record ResourceWithAllTypes(
        @ApiDescription("A type") boolean b,
        @ApiDescription("A type") int i,
        @ApiDescription("A type") long l,
        @ApiDescription("A type") double d,
        @ApiDescription("A type") String s,
        @ApiDescription("A type") Instant instant,
        @ApiDescription("A type") LocalDate localDate,
        @ApiDescription("A type") BigDecimal bigDecimal,
        @ApiDescription("A type") UUID uuid,
        @ApiDescription("A type") Stuff e,
        @ApiDescription("A type") Map<String, String> m,
        @ApiDescription("A type") List<Stuff> le,
        @ApiDescription("A type") Set<Stuff> se,
        @ApiDescription("A type") Optional<Boolean> ob,
        @ApiDescription("A type") Optional<Integer> oi,
        @ApiDescription("A type") Optional<Long> ol,
        @ApiDescription("A type") Optional<Double> od,
        @ApiDescription("A type") Optional<String> os,
        @ApiDescription("A type") Optional<Instant> oInstant,
        @ApiDescription("A type") Optional<LocalDate> oLocalDate,
        @ApiDescription("A type") Optional<BigDecimal> oBigDecimal,
        @ApiDescription("A type") Optional<Stuff> oe,
        @ApiDescription("A type") SimpleResource simpleResource,
        @ApiDescription("A type") List<SimpleResource> lSimpleResource,
        @ApiDescription("A type") Set<SimpleResource> sSimpleResource,
        @ApiDescription("A type") Optional<SimpleResource> oSimpleResource,
        @ApiDescription("A type") Optional<UUID> oUuid,
        @ApiDescription("A type") List<SimpleId> listOfNameIds,
        @ApiDescription("A type") Optional<List<SimpleId>> maybeNameIds,
        @ApiDescription("A type") Optional<SimpleId> maybeNameId,
        @ApiDescription("A type") PolyInPoly pp,
        @ApiDescription("A type") Optional<PolyInPoly> ppo)
{
    public enum Stuff
    {
        Small,
        Big,
        Large,
    }

    @ApiResource(name = "name", description = "A name")
    @ApiReadOnly
    public record SimpleResource(@ApiDescription("A name") String name)
    {
    }

    public static class SimpleId
            extends ApiStringId<SimpleResource>
    {
        public SimpleId()
        {
            super(UUID.randomUUID().toString());
        }

        @JsonCreator
        public SimpleId(String id)
        {
            super(id);
        }
    }
}
