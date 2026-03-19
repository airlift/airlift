/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.test;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiDelete;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.test.model.NewWidget;
import io.airlift.test.model.Widget;
import io.airlift.test.model.WidgetId;
import io.airlift.test.model.WidgetPatch;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Widget management service demonstrating CRUD operations with pagination and filtering.
 */
@ApiService(name = "widgets", type = TestServiceType.class, description = "Widget management service")
public class WidgetService
{
    private final Map<String, Widget> widgets = new ConcurrentHashMap<>();

    public WidgetService()
    {
        // Pre-populate with some sample widgets
        createWidget(new NewWidget("Alpha Widget", "First test widget"));
        createWidget(new NewWidget("Beta Widget", "Second test widget"));
        createWidget(new NewWidget("Gamma Widget", "Third test widget"));
    }

    @ApiList(description = "List all widgets with pagination and filtering")
    public ApiPaginatedResult<Widget> listWidgets(
            @ApiParameter ApiPagination pagination,
            @ApiParameter(allowedValues = {"name", "status", "createdAt"}) ApiOrderBy orderBy,
            @ApiParameter ApiFilter nameFilter)
    {
        List<Widget> allWidgets = ImmutableList.copyOf(widgets.values());

        // Apply name filter if present
        Optional<String> filterValue = nameFilter.mapAsString();
        List<Widget> filteredWidgets = filterValue
                .map(filter -> allWidgets.stream()
                        .filter(w -> w.name().toLowerCase().contains(filter.toLowerCase()))
                        .toList())
                .orElse(allWidgets);

        // Simple pagination - in real implementation would use page tokens
        int pageSize = pagination.pageSize();
        List<Widget> pagedWidgets = filteredWidgets.stream()
                .limit(pageSize)
                .toList();

        String nextPageToken = pagedWidgets.size() < filteredWidgets.size() ? "next" : "";
        return new ApiPaginatedResult<>(nextPageToken, pagedWidgets);
    }

    @ApiGet(description = "Get a widget by ID")
    public Widget getWidget(@ApiParameter WidgetId widgetId)
    {
        Widget widget = widgets.get(widgetId.value());
        if (widget == null) {
            throw new RuntimeException("Widget not found: " + widgetId);
        }
        return widget;
    }

    @ApiCreate(description = "Create a new widget")
    public Widget createWidget(NewWidget newWidget)
    {
        String id = UUID.randomUUID().toString();
        Widget widget = new Widget(
                new WidgetId(id),
                newWidget.name(),
                newWidget.description(),
                Instant.now());
        widgets.put(id, widget);
        return widget;
    }

    @ApiUpdate(description = "Update an existing widget")
    public Widget updateWidget(@ApiParameter WidgetId widgetId, WidgetPatch patch)
    {
        Widget existing = widgets.get(widgetId.value());
        if (existing == null) {
            throw new RuntimeException("Widget not found: " + widgetId);
        }

        Widget updated = new Widget(
                existing.widgetId(),
                patch.name().orElse(existing.name()),
                patch.description().orElse(existing.description()),
                existing.createdAt());
        widgets.put(widgetId.value(), updated);
        return updated;
    }

    @ApiDelete(description = "Delete a widget")
    public void deleteWidget(@ApiParameter WidgetId widgetId)
    {
        Widget removed = widgets.remove(widgetId.value());
        if (removed == null) {
            throw new RuntimeException("Widget not found: " + widgetId);
        }
    }
}
