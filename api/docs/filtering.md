[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Filtering

An `ApiFilter`/`ApiFilterList` parameter is used to receive each filter request. It contains the value specified
for the filter. The parameter name is used for the filter name (and should signal to users what field is being filtered on).

E.g.

```java
@ApiGet(...)
public MyResource myFilteredGet(@ApiParameter ApiFilter name, @ApiParameter ApiFilter group)
{
    ...
}
```

Filtering supports single values or multiple values. For multiple values, multiple query parameters
with the filter name will be made into a list. Use `ApiFilterList` as the parameter.
