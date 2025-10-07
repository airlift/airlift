package io.airlift.api.servertests.standard;

import io.airlift.api.ApiId;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.TestId;

@ApiIdSupportsLookup
public class LookupId
        extends ApiId<Thing, TestId>
{
    public LookupId()
    {
        super("");
    }

    public LookupId(TestId internalId)
    {
        super(internalId);
    }

    public LookupId(String id)
    {
        super(id);
    }
}
