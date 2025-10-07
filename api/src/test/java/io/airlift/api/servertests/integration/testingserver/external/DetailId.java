package io.airlift.api.servertests.integration.testingserver.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiStringId;

public class DetailId
        extends ApiStringId<Detail>
{
    public DetailId()
    {
        super("example");
    }

    @JsonCreator
    public DetailId(String id)
    {
        super(id);
    }
}
