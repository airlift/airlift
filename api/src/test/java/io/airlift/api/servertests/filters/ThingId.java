package io.airlift.api.servertests.filters;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class ThingId
        extends ApiId<Thing, TestId>
{
    public ThingId()
    {
        this("dummy");
    }

    public ThingId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public ThingId(String id)
    {
        super(id);
    }
}
