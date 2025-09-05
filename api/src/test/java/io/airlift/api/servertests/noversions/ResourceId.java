package io.airlift.api.servertests.noversions;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class ResourceId
        extends ApiStringId<ResourceWithoutVersion>
{
    public ResourceId()
    {
        super("example");
    }

    @JsonCreator
    public ResourceId(String id)
    {
        super(id);
    }
}
