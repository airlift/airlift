[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Multi-part Uploads

`@ApiCreate` and `@ApiUpdate` methods can use `multipart/form-data` instead of `application/json` to take advantage
of file uploads, etc. To do this, wrap the payload resource in one of the `ApiMultiPartForm` wrappers. `ApiMultiPartForm` has two variants:
`ApiMultiPartFormWithResource` and `ApiMultiPartForm`. `ApiMultiPartFormWithResource` is used when the resource is the first item in the form and 
`ApiMultiPartForm` is used when the resource is not in the form.

E.g.

```java
@ApiUpdate(...)
public void myFilteredGet(ApiMultiPartFormWithResource<MyResource> multiPart)
{
    MyResource resource = multiPart.resource();

    multiPart.itemInputIterator().forEachRemaining(item -> {
        item.name()
        ... etc ...
                
        item.inputStream()
        ... etc ...
    });
}
```
