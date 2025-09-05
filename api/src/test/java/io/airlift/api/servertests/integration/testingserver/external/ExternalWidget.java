package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiUnwrapped;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidget;

import static io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetSize.toInternal;
import static java.util.Objects.requireNonNull;

@ApiResource(name = "widget", description = "Something amazing", quotas = "HIDDEN")
public record ExternalWidget(
        @ApiDescription("Widget id") @ApiReadOnly ExternalWidgetId widgetId,
        ApiResourceVersion syncToken,
        @ApiUnwrapped NewExternalWidget widgetDefinition)
{
    public ExternalWidget
    {
        requireNonNull(syncToken, "syncToken is null");
        requireNonNull(widgetId, "widgetId is null");
        requireNonNull(widgetDefinition, "widgetDefinition is null");
    }

    public InternalWidget map()
    {
        return new InternalWidget(
                widgetId.toInternal(),
                syncToken.version(),
                widgetDefinition.name(),
                toInternal(widgetDefinition.size()),
                widgetDefinition.manufactureDate(),
                widgetDefinition.attributes());
    }
}
