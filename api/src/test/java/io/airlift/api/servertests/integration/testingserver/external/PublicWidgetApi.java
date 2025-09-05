package io.airlift.api.servertests.integration.testingserver.external;

import com.google.inject.Inject;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDelete;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiByteStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ApiUpdate;
import io.airlift.api.servertests.integration.testingserver.external.Detail.NameAndAge;
import io.airlift.api.servertests.integration.testingserver.external.Detail.Schedule;
import io.airlift.api.servertests.integration.testingserver.internal.InternalController;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidget;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidgetId;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static io.airlift.api.ApiType.CREATE;
import static io.airlift.api.ApiType.GET;
import static io.airlift.api.responses.ApiException.badRequest;
import static io.airlift.api.responses.ApiException.notFound;
import static java.util.Objects.requireNonNull;

@ApiService(name = "widget", type = WidgetServiceType.class, description = "We all love widgets")
public class PublicWidgetApi
{
    private final InternalController controller;
    private final ApiQuotaController quotaController;

    @Inject
    public PublicWidgetApi(InternalController controller, ApiQuotaController quotaController)
    {
        this.controller = requireNonNull(controller, "controller is null");
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @ApiList(description = "List all widgets")
    public ApiPaginatedResult<ExternalWidget> listWidgets(@ApiParameter ApiPagination pagination, @ApiParameter(allowedValues = {"id", "name", "size"}) ApiOrderBy ordering, @ApiParameter ApiFilter name, @ApiParameter ApiFilterList size)
    {
        return controller.list(pagination.withOrdering(ordering), name, size).map(InternalWidget::map);
    }

    @ApiGet(description = "Get a widget")
    public ExternalWidget getWidget(@ApiParameter ExternalWidgetId id)
    {
        InternalWidget widget = controller.get(id.toInternal()).orElseThrow(() -> notFound("No tenemos"));
        return widget.map();
    }

    @ApiCreate(description = "Create a new widget")
    public ExternalWidget createWidget(@Context Request request, NewExternalWidget widget)
    {
        InternalWidget newWidget = controller.create(request, new InternalWidget(new InternalWidgetId(), 1, widget.name(), InternalWidget.map(widget.size()), widget.manufactureDate(), widget.attributes()));
        return newWidget.map();
    }

    @ApiDelete(description = "Delete a widget")
    public ExternalWidget deleteWidget(@ApiParameter ExternalWidgetId id)
    {
        return controller.delete(id.toInternal()).map(InternalWidget::map)
                .orElseThrow(() -> notFound("Nope - aint got it"));
    }

    @ApiUpdate(description = "Patch a widget")
    public ExternalWidget patchWidget(@ApiParameter ExternalWidgetId id, ApiPatch<ExternalWidget> patchedExternalWidget)
    {
        InternalWidget updatedInternalWidget = controller.update(id.toInternal(), internal -> patchedExternalWidget.apply(internal.map()).map())
                .orElseThrow(() -> notFound("Not this time"));

        return updatedInternalWidget.map();
    }

    @ApiCustom(verb = "hidden", type = GET, description = "Use an OpenApiFilter to hide this")
    public StringResult hiddenMethod()
    {
        return new StringResult("hidden success");
    }

    @ApiCreate(description = "Test of polymorphic resources")
    public StringResult createDetail(@Context Request request, Detail detail)
    {
        quotaController.recordQuotaUsage(request, "DETAIL");

        return new StringResult("Detail created: " + detail.getClass().getSimpleName());
    }

    @ApiGet(description = "Test of polymorphic resources", openApiAlternateName = "specialDetailGetter")
    public DetailResult getDetail(@ApiParameter DetailId detailId)
    {
        return switch (detailId.toString()) {
            case "1" -> new DetailResult(detailId, new ApiResourceVersion(1), new NameAndAge("A name", 42));
            case "2" -> new DetailResult(detailId, new ApiResourceVersion(1), new Schedule("A schedule", Instant.now(), Instant.now().plusSeconds(60)));
            default -> throw badRequest("Unknown ID");
        };
    }

    @ApiCustom(type = GET, verb = "streamChars", description = "Test of streaming response")
    public ApiTextStreamResponse<ExternalWidget> streamText()
    {
        return new ApiTextStreamResponse<>("This is a test");
    }

    @ApiCustom(type = GET, verb = "streamBytes", description = "Test of streaming response")
    public ApiByteStreamResponse<ExternalWidget> streamBytes()
    {
        return new ApiByteStreamResponse<>("This is a test".getBytes(StandardCharsets.UTF_8));
    }

    @ApiCustom(verb = "recursive", type = CREATE, description = "Test of recursive resources", quotas = "RECURSIVE")
    public StringResult recursiveTest(@Context Request request, RecursiveDetail recursiveDetail)
    {
        quotaController.recordQuotaUsage(request, "RECURSIVE");

        return new StringResult("Recursive: %s, %s".formatted(recursiveDetail.name(), recursiveDetail.items().getFirst().recursiveDetails().getFirst().name()));
    }
}
