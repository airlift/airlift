package io.airlift.mcp.operations;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.internal.InternalEntities;
import io.airlift.mcp.model.PaginatedRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

public class PaginationUtil
{
    private final int pageSize;

    public PaginationUtil(McpConfig mcpConfig)
    {
        pageSize = mcpConfig.getDefaultPageSize();
    }

    // InternalEntities returns tools, prompts, resources and templates ordered by name, and skills by URI.
    // Directory children and custom providers have no such ordering guarantee.
    public <T, L> T paginateEntityList(McpEntities entities, PaginatedRequest request, List<L> items, Function<L, String> keyMapper, BiFunction<List<L>, Optional<String>, T> resultMapper)
    {
        if (entities.getClass() != InternalEntities.class) {
            return paginate(request, items, keyMapper, resultMapper);
        }

        int start = 0;
        if (request.cursor().isPresent()) {
            String cursor = request.cursor().orElseThrow();
            int end = items.size();
            while (start < end) {
                int middle = (start + end) >>> 1;
                if (keyMapper.apply(items.get(middle)).compareTo(cursor) <= 0) {
                    start = middle + 1;
                }
                else {
                    end = middle;
                }
            }
        }
        return page(items, start, keyMapper, resultMapper);
    }

    public <T, L> T paginate(PaginatedRequest request, List<L> items, Function<L, String> keyMapper, BiFunction<List<L>, Optional<String>, T> resultMapper)
    {
        Stream<L> itemsStream = request.cursor()
                .map(cursor -> items.stream()
                        .filter(item -> keyMapper.apply(item).compareTo(cursor) > 0))
                .orElseGet(items::stream);

        List<L> filteredList = itemsStream.sorted(comparing(keyMapper))
                .collect(toImmutableList());

        return page(filteredList, 0, keyMapper, resultMapper);
    }

    private <T, L> T page(List<L> items, int start, Function<L, String> keyMapper, BiFunction<List<L>, Optional<String>, T> resultMapper)
    {
        int end = start + Math.min(pageSize, items.size() - start);
        List<L> page = ImmutableList.copyOf(items.subList(start, end));
        Optional<String> nextCursor = end < items.size() ? Optional.of(keyMapper.apply(page.getLast())) : Optional.empty();
        return resultMapper.apply(page, nextCursor);
    }
}
