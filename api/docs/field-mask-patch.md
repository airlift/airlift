[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Field Mask Patch Details

With field mask patching, clients provide a complete model object and specify which fields to update
via field mask query parameters. This method is meant for hard updates with a pre-fetched record. Clients
must specify an ID and syncToken.

To add a field mask patch method to your API, use `@ApiUpdate` with an `ApiPatch` argument.
E.g.

```java
@ApiUpdate(...)
public Widget patchWidget(ApiPatch<Widget> patch)
{
    // ...
}
```

API Builder transforms this into a field mask patch. Clients call these endpoints like this:

```shell
curl -X PATCH -H "Content-Type: application/json" <host>/public/api/v1/widget?fieldMask=name&fieldMask=age -d '
    {
        "id": "12345678,
        "name": "updated",
        "age": 50,
        "location": "somewhere",
        "quota": 101,
        "syncToken": 5
    }
'
```

### Using Field Mask Patch With a Database

Normally, in a transaction, you do a `SELECT FOR UPDATE` to get the original value, apply the field mask patch to the
original value to get a new/updated value and then write that to the database.

E.g.

```java
// Imagine you have a database controller that has an update widget method
// ... in the controller ...
public Optional<Widget> updateWidget(ID widgetId, long version, UnaryOperator<Widget> updateOperator)
{
    db.inTransaction(handle -> {
        Optional<Widget> oldValue = handle.query("SELECT FOR UPDATE * from widget WHERE id = :widgetId")
        Optional<Widget> newValue = oldValue.map(updateOperator);
        return newValue.map(value -> handle.execute("UPDATE widget ... etc ... WHERE id = :widgetId AND version = :version"));
    });
}

// invoke this controller using the patch instance
updateWidget(id, patch::apply); // creates a new instance from the old value with any new field values specified by the field mask 
```
