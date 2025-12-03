package io.airlift.api.compatibility;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;

import java.util.Set;

public class DummyServiceType
        implements ApiServiceType
{
    @Override
    public String id()
    {
        return "id";
    }

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String title()
    {
        return "dummy";
    }

    @Override
    public String description()
    {
        return "dummy";
    }

    @Override
    public Set<ApiServiceTrait> traits()
    {
        return ImmutableSet.of();
    }
}
