package io.airlift.api.servertests.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class PolyResourceId
        extends ApiStringId<PolyResource>
{
    public PolyResourceId()
    {
        super("dummy");
    }

    @JsonCreator
    public PolyResourceId(String id)
    {
        super(id);
    }
}
