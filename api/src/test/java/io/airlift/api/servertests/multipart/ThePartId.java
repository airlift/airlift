package io.airlift.api.servertests.multipart;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class ThePartId
        extends ApiStringId<ThePart>
{
    public ThePartId()
    {
        super("dummy");
    }

    @JsonCreator
    public ThePartId(String id)
    {
        super(id);
    }
}
