[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Custom Methods

> Google Guide Link: https://cloud.google.com/apis/design/custom_methods

## Background

From the Google Guide:

> Custom methods refer to API methods besides the 5 standard methods. They should only be used for functionality 
> that cannot be easily expressed via standard methods. In general, API designers should choose standard methods 
> over custom methods whenever feasible. Standard Methods have simpler and well-defined semantics that most 
> developers are familiar with, so they are easier to use and less error prone.

## Usage

```java
@ApiCustom(verb="verb", description="Method documentation" <optional attributes>)
public Foo apiMethod(...)
{
    ...
}
```

### Annotation Attributes

| Attribute   | Description                                                                                                       |
|-------------|-------------------------------------------------------------------------------------------------------------------|
| verb        | The custom method verb. Will get added to the path as a matrix parameter.                                         |
| description | User displayable documentation for the method                                                                     |
| type        | Optional - which API Builder type to map to (controls the HTTP Method). The default is `CREATE` (i.e. HTTP POST). |
| responses   | Optional - same as for other method type annotations                                                              |
