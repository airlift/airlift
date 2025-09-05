package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.TestId;

@ApiIdSupportsLookup
public class BadLookupId
        extends ApiId<Food, TestId>
{
    public BadLookupId()
    {
        this("dummy");
    }

    public BadLookupId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public BadLookupId(String id)
    {
        super(id);
    }
}
