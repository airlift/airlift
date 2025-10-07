package io.airlift.api.servertests.streaming;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class StreamingResourceId
        extends ApiStringId<StreamingResource>
{
    public StreamingResourceId()
    {
        super("dummy");
    }

    @JsonCreator
    public StreamingResourceId(String id)
    {
        super(id);
    }
}
