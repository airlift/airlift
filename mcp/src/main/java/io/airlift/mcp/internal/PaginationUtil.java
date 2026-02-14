package io.airlift.mcp.internal;

import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.model.PaginatedRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

class PaginationUtil
{
    private final int pageSize;

    @Inject
    PaginationUtil(McpConfig mcpConfig)
    {
        pageSize = mcpConfig.getDefaultPageSize();
    }

    int pageSize()
    {
        return pageSize;
    }

    <T, L> T paginate(PaginatedRequest request, List<L> items, Function<L, String> keyMapper, BiFunction<List<L>, Optional<String>, T> resultMapper)
    {
        Stream<L> itemsStream = request.cursor()
                .map(cursor -> items.stream()
                        .filter(item -> keyMapper.apply(item).compareTo(cursor) > 0))
                .orElseGet(items::stream);

        List<L> filteredList = itemsStream.sorted(comparing(keyMapper))
                .collect(toImmutableList());

        if (filteredList.size() <= pageSize) {
            return resultMapper.apply(filteredList, Optional.empty());
        }

        List<L> subItems = filteredList.subList(0, pageSize);
        String lastKey = keyMapper.apply(subItems.getLast());

        return resultMapper.apply(subItems, Optional.of(lastKey));
    }
}
