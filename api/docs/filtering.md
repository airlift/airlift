[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Filtering

Use `ApiFilter<T>` for a single filter value and `ApiFilterList<T>` for multiple values.
The Java parameter name defines the query parameter name.

```java
@ApiList(description = "List resources")
public List<MyResource> list(
        @ApiParameter ApiFilter<String> name,
        @ApiParameter ApiFilter<Instant> createdAfter,
        @ApiParameter ApiFilterList<Integer> group)
{
    return controller.list(name.value(), createdAfter.value(), group.values());
}
```

`ApiFilter<T>.value()` returns `Optional<T>`. A missing query parameter produces `Optional.empty()`.
If the query parameter occurs more than once, request validation returns HTTP 400.
Use `ApiFilterList<T>` to accept multiple values.

`ApiFilterList<T>.values()` returns an immutable `List<T>`. A missing query parameter produces an empty list.
Repeat the query parameter to provide multiple values. For example, `?group=1&group=2` produces
`List.of(1, 2)`. Values keep their query order. Commas do not split values.

The supported type arguments are `Boolean`, `Integer`, `Long`, `Double`, `String`, `Instant`, `UUID`,
and concrete enums. Binding converts each query value before the API method runs. Invalid values return
HTTP 400. Boolean values must be `true` or `false`, ignoring case. Double values must be finite.

`Instant` filter values must be ISO-8601 strings accepted by `Instant.parse`, such as
`2026-06-10T12:13:14Z`. OpenAPI describes `ApiFilter<Instant>` as `type: string` with
`format: date-time`. For `ApiFilterList<Instant>`, each array item has this schema.
For the `createdAfter` parameter above, a valid query is `?createdAfter=2026-06-10T12:13:14Z`.

Use an enum to define the filter values:

```java
public enum Status
{
    Active,
    Inactive,
}

@ApiList(description = "List resources by status")
public List<MyResource> listByStatus(@ApiParameter ApiFilterList<Status> status)
{
    return controller.listByStatus(status.values());
}
```

Both `ApiFilter<Status>` and `ApiFilterList<Status>` use the configured `ApiEnumValueResolver`
to match query values to enum constants. OpenAPI describes filter values and list items using `T`.
Enum values also use the configured resolver.

When building the API model, resolved enum wire values must satisfy the service's
`enumNamingFormat()`, just as enums in body resources do. The default resolver also rejects invalid
`@JsonValue` definitions and duplicate wire values. These checks run even when OpenAPI is disabled
or `ENUMS_AS_STRINGS` is enabled. Requests must match the resolved wire values exactly.
The naming format validates values; it does not convert their case.

Raw `ApiFilter` and `ApiFilterList` declarations, and declarations with `<Object>`, retain string binding
for compatibility. The `map` method and the `mapAsString`, `mapAsInt`, `mapAsDouble`, `mapAsInstant`,
and `mapAsBoolean` helpers remain available. Use supported concrete types for automatic conversion.
Other type arguments, wildcards, and type variables are rejected when the API model is built.
