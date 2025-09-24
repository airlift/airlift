[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Validate Only

An `ApiValidateOnly` parameter is used to receive a validate-only request. It contains a
boolean indicating whether or not validate-only was requested. How an operation manages validate-only
is not defined. Generally, validate the arguments and return a response without causing any side effects
to the database, etc.

E.g.

```java
@ApiUpdate(...)
public void update(@ApiParameter ApiValidateOnly validateOnly, MyResource resource)
{
    if (validateOnly.requested()) {
        // do any validations
    }
    else{
        controller.doUpdate(resource);
    }
}
```
