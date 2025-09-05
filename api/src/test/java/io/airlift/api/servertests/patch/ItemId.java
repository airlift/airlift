package io.airlift.api.servertests.patch;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.TestId;

public class ItemId
        extends ApiId<Item, TestId>
{
    public ItemId()
    {
        this("default");
    }

    public ItemId(TestId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public ItemId(String id)
    {
        super(id);
    }
}
