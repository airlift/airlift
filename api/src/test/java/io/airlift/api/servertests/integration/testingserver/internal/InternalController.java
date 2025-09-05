package io.airlift.api.servertests.integration.testingserver.internal;

import com.google.inject.Inject;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiOrderBy.Ordering;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.servertests.integration.testingserver.TestingQuotaType;
import jakarta.ws.rs.core.Request;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.ApiOrderByDirection.ASCENDING;
import static io.airlift.api.ApiOrderByDirection.DESCENDING;
import static io.airlift.api.ApiPaginatedResult.EMPTY_PAGE_TOKEN;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class InternalController
{
    private final ApiQuotaController quotaController;
    private final Map<InternalWidgetId, InternalWidget> simulatedDatabase = new ConcurrentHashMap<>();

    @Inject
    public InternalController(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    public ApiPaginatedResult<InternalWidget> list(ApiPagination pagination, ApiFilter name, ApiFilterList size)
    {
        Ordering ordering = pagination.ordering().orElseGet(() -> new Ordering("id", ASCENDING));

        Comparator<InternalWidget> idComparator = Comparator.comparingInt(widget -> widget.id().id());

        Comparator<InternalWidget> sortComparator = switch (ordering.field().toLowerCase(Locale.ROOT)) {
            case "name" -> comparing(InternalWidget::name).thenComparing(idComparator);
            case "size" -> comparing(InternalWidget::size).thenComparing(idComparator);
            default -> idComparator;
        };
        if (ordering.direction() == DESCENDING) {
            sortComparator = sortComparator.reversed();
        }

        Predicate<InternalWidget> nameFilter = name.mapAsString().map(n -> (Predicate<InternalWidget>) widget -> widget.name().equals(n))
                .orElseGet(() -> _ -> true);

        List<Integer> sizeList = size.mapAsInt();
        Predicate<InternalWidget> sizeFilter = widget -> (sizeList.isEmpty() || sizeList.contains(widget.size()));

        int pageOffset = pagination.pageToken().map(Integer::parseInt).orElse(0);

        List<InternalWidget> widgets = simulatedDatabase.values().stream()
                .sorted(sortComparator)
                .filter(nameFilter.and(sizeFilter))
                .skip((long) pageOffset * pagination.pageSize())
                .limit(pagination.pageSize())
                .collect(toImmutableList());

        String nextPageToken = widgets.isEmpty() ? EMPTY_PAGE_TOKEN : Integer.toString(pageOffset + 1);

        return new ApiPaginatedResult<>(nextPageToken, widgets);
    }

    public Optional<InternalWidget> get(InternalWidgetId id)
    {
        return Optional.ofNullable(simulatedDatabase.get(id));
    }

    public InternalWidget create(Request request, InternalWidget widget)
    {
        // note: in real applications an actual quota should be applied
        quotaController.recordQuotaUsage(request, TestingQuotaType.WIDGETS.name());

        simulatedDatabase.put(widget.id(), widget);

        return widget;
    }

    public Optional<InternalWidget> delete(InternalWidgetId id)
    {
        return Optional.ofNullable(simulatedDatabase.remove(id));
    }

    public Optional<InternalWidget> update(InternalWidgetId id, UnaryOperator<InternalWidget> updateOperator)
    {
        return Optional.ofNullable(simulatedDatabase.computeIfPresent(id, (_, widget) -> updateOperator.apply(widget)));
    }
}
