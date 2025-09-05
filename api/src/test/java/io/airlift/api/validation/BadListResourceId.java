package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class BadListResourceId
        extends ApiId<BadListResource, TestId>
{
    public BadListResourceId()
    {
        this("dummy");
    }

    public BadListResourceId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public BadListResourceId(String id)
    {
        super(id);
    }
}
