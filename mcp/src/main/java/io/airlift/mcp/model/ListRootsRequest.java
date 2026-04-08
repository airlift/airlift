package io.airlift.mcp.model;

import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;

public record ListRootsRequest()
        implements InputRequest
{
    @Override
    public String methodName()
    {
        return METHOD_ROOTS_LIST;
    }

    @Override
    public Class<? extends InputResponse> responseType()
    {
        return ListRootsResult.class;
    }
}
