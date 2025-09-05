package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class DuplicateThingId
        extends ApiId<Thing, TestId>
{
    public DuplicateThingId()
    {
        this("dummy");
    }

    public DuplicateThingId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public DuplicateThingId(String id)
    {
        super(id);
    }
}
