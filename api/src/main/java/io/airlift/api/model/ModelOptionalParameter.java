package io.airlift.api.model;

import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelOptionalParameter(Location location, Type type, Collection<Metadata> metadata, Collection<ExternalParameter> externalParameters, Set<String> limitedValues)
        implements Comparable<ModelOptionalParameter>
{
    public enum Location
    {
        QUERY,
        HEADER
    }

    public enum Metadata
    {
        MULTIPLE_ALLOWED,
        VALIDATES_ARGUMENT,
        REQUIRES_ALLOWED_VALUES,
    }

    public record ExternalParameter(String name, String description, Class<?> externalType)
    {
        public ExternalParameter
        {
            requireNonNull(name, "name is null");
            requireNonNull(externalType, "externalType is null");
            requireNonNull(description, "description is null");
        }

        public ExternalParameter withName(String name)
        {
            return new ExternalParameter(name, description, externalType);
        }

        public ExternalParameter withDescription(String description)
        {
            return new ExternalParameter(name, description, externalType);
        }
    }

    public ModelOptionalParameter
    {
        requireNonNull(location, "location is null");
        requireNonNull(type, "type is null");
        metadata = ImmutableSet.copyOf(metadata);
        externalParameters = ImmutableSet.copyOf(externalParameters);
        limitedValues = ImmutableSet.copyOf(limitedValues);
    }

    public ModelOptionalParameter(Location location, Type type, Collection<ExternalParameter> externalParameters)
    {
        this(location, type, ImmutableSet.of(), externalParameters, ImmutableSet.of());
    }

    public ModelOptionalParameter withExternalParameters(Collection<ExternalParameter> externalParameters)
    {
        return new ModelOptionalParameter(location, type, metadata, externalParameters, limitedValues);
    }

    public ModelOptionalParameter withMetadata(Metadata... metadata)
    {
        return new ModelOptionalParameter(location, type, Arrays.asList(metadata), externalParameters, limitedValues);
    }

    public ModelOptionalParameter withLimitedValues(Set<String> limitedValues)
    {
        return new ModelOptionalParameter(location, type, metadata, externalParameters, limitedValues);
    }

    @Override
    public int compareTo(ModelOptionalParameter rhs)
    {
        return toString().compareTo(rhs.toString());
    }
}
