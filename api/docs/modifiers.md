[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Modifiers and Headers

Request modifiers are boolean query parameters that can optionally modify a request. An `ApiModifier` parameter is used to 
receive each request modifiers. The parameter name is used as the query parameter name that clients specify for the modifier.

Request headers are string header parameters. An `ApiHeader` parameter is used to
receive each request header. The parameter name is used as base for the header name of the form: `X-<uppercase-basename>`.

The `ApiResponseHeaders` instance allows for setting any response header.

E.g.

```java
@ApiGet(...)
public MyResource myFilteredGet(@ApiParameter ApiModifier forceLoad, @ApiParameter ApiHeader trace, @ApiParameter ApiResponseHeaders responseHeaders)
{
    ...
}
```
