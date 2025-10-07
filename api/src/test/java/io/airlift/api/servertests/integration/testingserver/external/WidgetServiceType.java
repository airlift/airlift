package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiServiceType;

public class WidgetServiceType
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
        return "The best widget service";
    }

    @Override
    public String description()
    {
        return "You know we have the best widgets";
    }
}
