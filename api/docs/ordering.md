[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Ordering

> Google Guide Link: https://cloud.google.com/apis/design/design_patterns#sorting_order

An `ApiOrderBy` parameter is used to receive ordering requests. Users specify ordering with the 
query parameter `orderBy`. The format is similar to standard SQL ORDER BY clauses. You must specify
the allowed sort fields in the `@ApiParameter`'s `allowedValues`.

E.g.

```java
@ApiGet(...)
public MyResource myOrderedGet(@ApiParameter(allowedValues = {"name", "size"}) ApiOrderBy ordering)
{
    ...
}
```

#### Pagination Integration

Ordering integrates with pagination. Combining `ApiOrderBy` with `ApiPagination` you can apply ordering to
a pagination instance:

```
ApiPagination pagination = ...
ApiOrderBy orderBy = ...
ApiPagination orderedPagination = pagination.withOrdering(orderBy);
```

See [pagination for details](pagination.md).
