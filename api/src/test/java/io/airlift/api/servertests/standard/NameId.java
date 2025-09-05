package io.airlift.api.servertests.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class NameId
        extends ApiStringId<Name>
{
    public NameId()
    {
        super("dummy");
    }

    @JsonCreator
    public NameId(String id)
    {
        super(id);
    }

    @Override
    public Wrapper toInternal()
    {
        return super.toInternal();
    }
}
