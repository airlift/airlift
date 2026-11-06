package io.airlift.api;

import io.airlift.api.ApiOrderBy.Ordering;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.airlift.api.ApiOrderByDirection.ASCENDING;
import static io.airlift.api.ApiOrderByDirection.DESCENDING;
import static io.airlift.api.ApiPaginatedResult.EMPTY_PAGE_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestApiPagination
{
    @Test
    public void testWithOrdering()
    {
        ApiPagination<String> pagination = new ApiPagination<>(Optional.of("next"), 25, Optional.empty());
        assertThat(pagination.withOrdering(new ApiOrderBy<>(List.of()))).isEqualTo(pagination);

        ApiOrderBy<String> orderBy = new ApiOrderBy<>("name", ASCENDING);
        ApiPagination<String> orderedPagination = pagination.withOrdering(orderBy);
        assertThat(orderedPagination).isEqualTo(new ApiPagination<>(Optional.of("next"), 25, Optional.of(new Ordering<>("name", ASCENDING))));
        assertThat(orderedPagination.withOrdering(new ApiOrderBy<>(List.of()))).isEqualTo(pagination);
    }

    @Test
    public void testWithEnumOrdering()
    {
        ApiPagination<Field> pagination = new ApiPagination<>(Optional.of("next"), 25, Optional.empty());
        assertThat(pagination.withOrdering(new ApiOrderBy<>(List.of()))).isEqualTo(pagination);

        ApiOrderBy<Field> orderBy = new ApiOrderBy<>(Field.NAME, DESCENDING);
        ApiPagination<Field> orderedPagination = pagination.withOrdering(orderBy);
        assertThat(orderedPagination).isEqualTo(new ApiPagination<>(Optional.of("next"), 25, Optional.of(new Ordering<>(Field.NAME, DESCENDING))));
        assertThat(orderedPagination.withOrdering(new ApiOrderBy<>(List.of()))).isEqualTo(pagination);
    }

    @Test
    public void testNextPreservesEnumOrdering()
    {
        ApiPagination<Field> pagination = new ApiPagination<>(Optional.empty(), 25, Optional.of(new Ordering<>(Field.NAME, DESCENDING)));

        ApiPagination<Field> next = pagination.next(new ApiPaginatedResult<>("next", List.of("first page")));
        assertThat(next).isEqualTo(new ApiPagination<>(Optional.of("next"), 25, pagination.ordering()));

        ApiPagination<Field> last = next.next(new ApiPaginatedResult<>(EMPTY_PAGE_TOKEN, List.of("last page")));
        assertThat(last).isEqualTo(pagination);
    }

    @Test
    public void testMultipleOrderingsRejected()
    {
        ApiPagination<String> pagination = new ApiPagination<>(Optional.empty(), 25, Optional.empty());
        ApiOrderBy<String> orderBy = new ApiOrderBy<>(List.of(new Ordering<>("name", ASCENDING), new Ordering<>("size", DESCENDING)));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> pagination.withOrdering(orderBy));
        assertThat(exception.getResponse().getStatus()).isEqualTo(400);

        ApiPagination<Field> enumPagination = new ApiPagination<>(Optional.empty(), 25, Optional.empty());
        ApiOrderBy<Field> enumOrderBy = new ApiOrderBy<>(List.of(new Ordering<>(Field.NAME, ASCENDING), new Ordering<>(Field.SIZE, DESCENDING)));

        WebApplicationException enumException = assertThrows(WebApplicationException.class, () -> enumPagination.withOrdering(enumOrderBy));
        assertThat(enumException.getResponse().getStatus()).isEqualTo(400);
    }

    private enum Field
    {
        NAME,
        SIZE,
    }
}
