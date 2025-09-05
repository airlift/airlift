[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Pagination

> Google Guide Link: https://cloud.google.com/apis/design/design_patterns#list_pagination)

An `ApiPagination` parameter is used to receive the pagination request. It contains an optional
page token and optional page size (0 means use the default). The API method returns an `ApiPaginatedResult` which wraps a normal
API resource but includes the next page token.

### E.g.

```java
@ApiList(...)
public ApiPaginatedResult<MyResources> list(@ApiParameter MyResourceId myResourceId, 
    @ApiParameter ApiPagination pagination)
{
    ...
}
```
