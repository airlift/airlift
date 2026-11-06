[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Ordering

> Google Guide Link: https://cloud.google.com/apis/design/design_patterns#sorting_order

An `ApiOrderBy<T>` parameter receives ordering requests through the query parameter `orderBy`.
The format is similar to standard SQL ORDER BY clauses, for example `name ASC,size DESC`.
Use `ApiOrderBy<String>` for string fields and specify the allowed fields in
`@ApiParameter.allowedValues`.

E.g.

```java
@ApiList(description = "List resources")
public List<MyResource> list(@ApiParameter(allowedValues = {"name", "size"}) ApiOrderBy<String> ordering)
{
    ...
}
```

Use a concrete enum as `T` to receive enum fields. The enum defines the allowed fields, so do not
specify `allowedValues`. Enum binding uses the configured `ApiEnumValueResolver`.

When building the API model, resolved enum wire values must satisfy the service's
`enumNamingFormat()`, just as enums in body resources do. The default resolver also rejects invalid
`@JsonValue` definitions and duplicate wire values. Requests must match the resolved wire values
exactly. The naming format validates values; it does not convert their case.

```java
public enum OrderByField
{
    Name,
    Size,
}

@ApiList(description = "List resources")
public List<MyResource> list(@ApiParameter ApiOrderBy<OrderByField> ordering)
{
    ...
}
```

Raw `ApiOrderBy` and `ApiOrderBy<Object>` also use string binding and require `allowedValues`.
Other type arguments, wildcards, and type variables are not supported in service parameters.

The `validate` methods accept fields of type `T`:

```java
ApiOrderBy<OrderByField> ordering = new ApiOrderBy<>(OrderByField.Name, ApiOrderByDirection.ASCENDING);
ordering.validate(OrderByField.Name, OrderByField.Size);
ordering.validate(Set.of(OrderByField.Name, OrderByField.Size));
```

#### Query Serialization

Use `Ordering.toQueryValue()` for string fields. For enum fields, pass the `ApiEnumValueResolver`
configured for the target API:

```java
String stringClause = new ApiOrderBy.Ordering<>("name", ApiOrderByDirection.ASCENDING)
        .toQueryValue();
String enumClause = new ApiOrderBy.Ordering<>(OrderByField.Name, ApiOrderByDirection.DESCENDING)
        .toQueryValue(enumValueResolver);
```

Calling the no-argument overload with an enum field throws `IllegalStateException`.
Calling the resolver overload with a string field also throws `IllegalStateException`.
A null resolver throws `NullPointerException`. These checks use the runtime field type.
Each overload formats one clause. Join multiple clauses with commas into one `orderBy` parameter,
then let the URI builder encode the query value.

#### Pagination Integration

`ApiPagination<T>` accepts ordering through `ApiOrderBy<T>`. Both must use the same field type.
Pagination allows at most one ordering clause.

```java
ApiPagination<String> pagination = ...
ApiOrderBy<String> orderBy = ...
ApiPagination<String> orderedPagination = pagination.withOrdering(orderBy);
```

Enum ordering stays typed through pagination:

```java
ApiPagination<OrderByField> pagination = ...
ApiOrderBy<OrderByField> orderBy = ...
ApiPagination<OrderByField> orderedPagination = pagination.withOrdering(orderBy);
```

See [pagination for details](pagination.md).
