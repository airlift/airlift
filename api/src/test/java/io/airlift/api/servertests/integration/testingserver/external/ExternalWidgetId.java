package io.airlift.api.servertests.integration.testingserver.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.airlift.api.ApiId;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidgetId;

public class ExternalWidgetId
        extends ApiId<ExternalWidget, InternalWidgetId>
{
    public ExternalWidgetId()
    {
        super("1");
    }

    public ExternalWidgetId(InternalWidgetId internalId)
    {
        super(internalId);
    }

    @JsonCreator
    public ExternalWidgetId(String id)
    {
        super(id);
    }
}
