package io.airlift.api.servertests.integration.testingserver.external;

import com.google.common.collect.ImmutableMap;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "widget", openApiAlternateName = "newWidget", description = "Something amazing", quotas = "WIDGETS")
public record NewExternalWidget(
        @ApiDescription("Widget size") ExternalWidgetSize size,
        @ApiDescription("Item name") String name,
        @ApiDescription("Date the item exited the factory") @ApiReadOnly Instant manufactureDate,
        @ApiDescription("Bag 'o' stuff") Map<String, String> attributes)
{
    public NewExternalWidget
    {
        requireNonNull(size, "size is null");
        requireNonNull(name, "name is null");
        requireNonNull(manufactureDate, "createdAt is null");
        attributes = ImmutableMap.copyOf(attributes);
    }
}
