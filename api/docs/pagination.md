[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Pagination

> Google Guide Link: https://cloud.google.com/apis/design/design_patterns#list_pagination)

An `ApiPagination<T>` parameter is used to receive the pagination request. It contains an optional
page token and optional page size (0 means use the default). The API method returns an `ApiPaginatedResult` which wraps a normal
API resource but includes the next page token.

### E.g.

```java
@ApiList(...)
public ApiPaginatedResult<MyResources> list(@ApiParameter MyResourceId myResourceId, 
    @ApiParameter ApiPagination<String> pagination)
{
    ...
}
```

The type parameter `T` is the ordering field type. Use `String` for string fields or a concrete enum
for enum fields. The pagination binding reads `pageToken` and `pageSize` and leaves ordering empty.
Attach ordering with `withOrdering`. Both parameters must use the same field type:

```java
@ApiList(description = "List resources")
public ApiPaginatedResult<MyResources> list(
        @ApiParameter ApiPagination<OrderByField> pagination,
        @ApiParameter ApiOrderBy<OrderByField> orderBy)
{
    ApiPagination<OrderByField> orderedPagination = pagination.withOrdering(orderBy);
    ...
}
```

`OrderByField` is a concrete enum. Do not specify `allowedValues` for enum ordering.
Pagination allows at most one ordering clause. `next` preserves the field type, ordering, and page size
while updating the page token. Raw `ApiPagination` remains supported for compatibility.

When `T` is a concrete enum, the API model validates its resolved wire values against the service's
`enumNamingFormat()`, even if the method has no `ApiOrderBy<T>` parameter. Enum definition checks use
the configured resolver, as they do for body resources. Ordering request values are parsed and
validated by `ApiOrderBy<T>` before they are attached to pagination.

When building a request URL from enum pagination, serialize the ordering with the resolver configured
for the target API:

```java
Optional<String> orderByQueryValue = orderedPagination.ordering()
        .map(ordering -> ordering.toQueryValue(enumValueResolver));
```

For string ordering, use `toQueryValue()` without a resolver.
