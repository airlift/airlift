package io.airlift.api.servertests.openapi.recursive;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class TableId
        extends ApiStringId<TransformLiveTableSchemaUpdate>
{
    public TableId()
    {
        super("dummy");
    }

    @JsonCreator
    public TableId(String id)
    {
        super(id);
    }
}
