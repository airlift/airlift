package io.airlift.mcp.client.internal;

import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.model.PaginatedResult;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;

public final class InternalPagedStream
{
    private InternalPagedStream() {}

    public static <T extends PaginatedResult, R> Stream<R> pagedStream(McpConnection connection, BiFunction<McpConnection, Optional<String>, T> pageProducer, Function<T, List<R>> mapper)
    {
        Iterator<R> iterator = new Iterator<>()
        {
            private Iterator<R> currentIterator;
            private Optional<String> cursor = Optional.empty();

            @Override
            public boolean hasNext()
            {
                while ((currentIterator == null) || !currentIterator.hasNext()) {
                    if ((currentIterator != null) && cursor.isEmpty()) {
                        return false;
                    }

                    T results = pageProducer.apply(connection, cursor);
                    cursor = results.nextCursor();
                    currentIterator = mapper.apply(results).iterator();
                }
                return true;
            }

            @Override
            public R next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return currentIterator.next();
            }
        };

        return StreamSupport.stream(spliteratorUnknownSize(iterator, ORDERED), false);
    }
}
