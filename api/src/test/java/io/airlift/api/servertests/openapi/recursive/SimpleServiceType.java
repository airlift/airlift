package io.airlift.api.servertests.openapi.recursive;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;

import java.util.Set;

public class SimpleServiceType
        implements ApiServiceType
{
    @Override
    public String id()
    {
        return "simple";
    }

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String title()
    {
        return "test";
    }

    @Override
    public String description()
    {
        return "test";
    }

    @Override
    public Set<ApiServiceTrait> traits()
    {
        return ImmutableSet.of();
    }
}
