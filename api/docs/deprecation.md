[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Deprecation

## Background

API methods can be marked as deprecated by using the `@ApiDeprecated` annotation. A newer implementation can be
optionally specified. API Builder will add deprecation response headers and indicate the deprecation in the Open API
documentation.

## Usage

```java
@ApiDeprecated(information = "User displayable details" <optional attributes>)
@Api...
public Foo apiMethod(...)
{
    ...
}
```

### Annotation Attributes

| Attribute                                          | Description                                                                                                                |
|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| information                                        | Used in the Open API method description to add details of the deprecation                                                  |
| deprecationDate                                    | Optional - date of deprecation. If not specified the deprecated header is set to `true`. Must be in the form: `YYYY-MM-DD` |
| newImplementationClass and newImplementationMethod | Optional - must be specified together. References the new implementation's class and method name                           |

## Deprecation Headers

> See https://datatracker.ietf.org/doc/html/draft-dalal-deprecation-header-03

| Header | Value                                                                                                        |
|--------|--------------------------------------------------------------------------------------------------------------|
| Deprecated | Either `true` or the date if deprecationDate was provided                                                    |
| Link | If newImplementationClass and newImplementationMethod are provided: `<updated-uri>; rel="successor-version"` | 
