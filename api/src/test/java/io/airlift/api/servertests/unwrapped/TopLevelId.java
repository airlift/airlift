package io.airlift.api.servertests.unwrapped;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class TopLevelId
        extends ApiStringId<TopLevel>
{
    public TopLevelId()
    {
        super("dummy");
    }

    @JsonCreator
    public TopLevelId(String id)
    {
        super(id);
    }
}
