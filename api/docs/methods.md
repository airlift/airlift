[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Methods

## Method Definition

> Google Guide Link: https://cloud.google.com/apis/design/standard_methods

An API method defines an operation on a resource or set of resources and returns a response indicating
the status of the operation. Methods are written as annotated Java methods in API Builder classes.

API Builder Methods have many restrictions. See [Verification and Enforcement](enforcement.md) for details.

## Method Types

Each Method is annotated based on the type of operation it performs. 

| Annotation   | Implied Type    | Details                    |
|--------------|-----------------|----------------------------|
| `@ApiGet`    | GET             | Return a single resource   |
| `@ApiList`   | GET             | Return a list of resources |
| `@ApiCreate` | POST            | Create a new resource      |
| `@ApiUpdate` | PUT or PATCH    | Update a resource          |
| `@ApiDelete` | DELETE          | Delete a resource          |
| `@ApiCustom` | any (see below) | Custom action              |

#### @ApiCustom

> Google Guide Link: https://cloud.google.com/apis/design/custom_methods

`@ApiCustom` allows defining custom methods that use a matrix parameter to specify the verb.
[See the @ApiCustom documentation](custom.md) for details.

#### Traits

Methods can be tagged with optional traits. There are currently two traits:

| Trait     | Description                                                                                                                                             |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PRIVATE` | The method will not show in the OpenAPI documentation or JSON                                                                                           |
| `BETA`    | The method will have a Beta message added to its OpenAPI description. Additionally, the method will _not_ be validated by the API Compatibility checker |
