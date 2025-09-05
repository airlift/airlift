[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Patch

## Patch Updates

For patching, clients specify the subset of fields to patch as the payload of the query. This method is meant
for in-place updates. The client does not need to pre-fetch the record.

To add a patch method to your API, use `@ApiUpdate` with an `ApiPatch` argument. E.g.

```java
@ApiUpdate(...)
public Widget patchWidget(ApiPatch<Widget> patch)
{
    // ...
}
```

API Builder transforms this into a patch. Clients call these endpoints like this:

```shell
curl -X PATCH -H "Content-Type: application/json" <host>/public/api/v1/widget/1000 -d '
    {
        "name": "updated"
    }
'
```
