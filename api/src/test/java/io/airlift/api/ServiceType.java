package io.airlift.api;

public class ServiceType
        implements ApiServiceType
{
    @Override
    public String id()
    {
        return "public";
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
}
