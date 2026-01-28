[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: OpenAPI

[Swagger/Open API spec](https://swagger.io/specification/) is automatically generated when you add an OpenApiMetadata instance to ApiModule.
When your application starts, the OpenAPI JSON spec is available at `/<service-type-id>/openapi/v<service-version-number>/json`.

Note: a custom URI prefix can be set via `OpenApiMetadata` properties.

If you want, you can render this JSON as interactive documentation using [Swagger UI](https://swagger.io/tools/swagger-ui/)
or [Redoc](https://github.com/Redocly/redoc).
