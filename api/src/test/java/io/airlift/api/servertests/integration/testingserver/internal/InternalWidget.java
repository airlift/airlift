package io.airlift.api.servertests.integration.testingserver.internal;

import com.google.common.collect.ImmutableMap;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidget;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetId;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetSize;
import io.airlift.api.servertests.integration.testingserver.external.NewExternalWidget;

import java.time.Instant;
import java.util.Map;

import static io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetSize.fromInternal;
import static java.util.Objects.requireNonNull;

public record InternalWidget(InternalWidgetId id, long version, String name, int size, Instant creationDate, Map<String, String> stuff)
{
    public static final int SIZE_HUGE = 100;
    public static final int SIZE_LARGE = 33;
    public static final int SIZE_SMALL = 5;

    public InternalWidget
    {
        requireNonNull(id, "id is null");
        requireNonNull(name, "name is null");
        requireNonNull(creationDate, "creationDate is null");
        stuff = ImmutableMap.copyOf(stuff);
    }

    public ExternalWidget map()
    {
        return new ExternalWidget(
                new ExternalWidgetId(id),
                new ApiResourceVersion(version),
                new NewExternalWidget(
                        fromInternal(size),
                        name,
                        creationDate,
                        stuff));
    }

    public static int map(ExternalWidgetSize size)
    {
        return switch (size) {
            case Huge -> SIZE_HUGE;
            case Large -> SIZE_LARGE;
            case Small -> SIZE_SMALL;
        };
    }
}
