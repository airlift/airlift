package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class AcceptableListResourceId
        extends ApiId<AcceptableListResource, TestId>
{
    public AcceptableListResourceId()
    {
        this("dummy");
    }

    public AcceptableListResourceId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public AcceptableListResourceId(String id)
    {
        super(id);
    }
}
