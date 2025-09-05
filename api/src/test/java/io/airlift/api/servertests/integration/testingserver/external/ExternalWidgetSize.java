package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiDescription;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidget;

import static io.airlift.api.responses.ApiException.badRequest;

public enum ExternalWidgetSize
{
    @ApiDescription("A small widget")
    Small,

    @ApiDescription("A large widget")
    Huge,

    @ApiDescription("A huge widget")
    Large;

    public static ExternalWidgetSize fromInternal(int internalSize)
    {
        return switch (internalSize) {
            case InternalWidget.SIZE_HUGE -> Huge;
            case InternalWidget.SIZE_LARGE -> Large;
            case InternalWidget.SIZE_SMALL -> Small;
            default -> throw badRequest("No mapping for size: " + internalSize);
        };
    }

    public static int toInternal(ExternalWidgetSize externalWidgetSize)
    {
        return switch (externalWidgetSize) {
            case Small -> InternalWidget.SIZE_SMALL;
            case Large -> InternalWidget.SIZE_LARGE;
            case Huge -> InternalWidget.SIZE_HUGE;
        };
    }
}
