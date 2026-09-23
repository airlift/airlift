[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: OpenAPI

[Swagger/Open API spec](https://swagger.io/specification/) is automatically generated when you add an OpenApiMetadata instance to ApiModule.
When your application starts, the OpenAPI JSON spec is available at `/<service-type-id>/openapi/v<service-version-number>/json`.

Note: a custom URI prefix can be set via `OpenApiMetadata` properties.

If you want, you can render this JSON as interactive documentation using [Swagger UI](https://swagger.io/tools/swagger-ui/)
or [Redoc](https://github.com/Redocly/redoc).

## Extensions

Operations can carry [specification extensions](https://swagger.io/specification/#specification-extensions). Register an
`OpenApiExtensionFilter` binding with `ApiModule` and it is applied to every generated operation, after the operation's
parameters, request body and responses have been built:

```java
ApiModule.builder()
        ...
        .addOpenApiExtensionFilterBinding(binding -> binding.to(MyExtensionFilter.class))
        .build();
```

Any number of filters can be registered, and a filter is an ordinary Guice binding, so it can inject whatever it needs to
decide what to publish. The order in which filters run is not defined: a filter must only add extensions that it owns, and
it is an error for a filter to overwrite an extension that another filter added. Extension names must start with `x-`.

A filter receives the `ModelService` and `ModelMethod` the operation was built from, so it can act on an application's own
annotations. For example, to mark which header carries an operation's idempotency key:

```java
public class IdempotencyFilter
        implements OpenApiExtensionFilter
{
    @Override
    public Operation apply(ModelService modelService, ModelMethod modelMethod, Operation operation)
    {
        IdempotencyKey idempotencyKey = modelMethod.method().getAnnotation(IdempotencyKey.class);
        if (idempotencyKey != null) {
            operation.addExtension("x-my-idempotency", Map.of("header", idempotencyKey.header()));
        }
        return operation;
    }
}
```

Use `@ApiParameter(name = "Idempotency-Key")` on the `ApiHeader` parameter so that the header is published and bound under
that exact name - see [Modifiers and Headers](modifiers.md).
