[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Introduction

## API Builder vs JAX-RS/Jersey

API Builder is a superset of JAX-RS/Jersey. It is a transpiler that takes a highly opinionated, rule-driven
specification and generates standard JAX-RS/Jersey API classes. The opinions and restrictions are based on the [Google Cloud API design guide](https://cloud.google.com/apis/design).

## Annotation Differences

| API Builder                                                                   | JAX-RS/Jersey                                 | Details                                                                                                   |
|-------------------------------------------------------------------------------|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `@ApiService`                                                                 | `@Path` (top level)                           | JAX-RS classes are annotated with `@Path`. API Builder uses `@ApiService` which defines service metadata. |
| `@ApiGet`, `@ApiList`, `@ApiCreate`, `@ApiUpdate`, `@ApiDelete`, `@ApiCustom` | `@GET`, `@PUT`, `@POST`, `@DELETE`, `@PATCH`  | API Builder uses a different set of HTTP method annotatons that better reflect their intended use.        |
| `@ApiParameter`                                                               | `@QueryParam`, `@PathParam` or `@HeaderParam` | API Builder has a set of objects that reference various parameters.                                       |

All JAX-RS annotations are prohibited except `@Context` and `@Suspended`. API Builder automatically generates API paths, path parameters,
content types, etc.

## API Builder Service Classes

- API Builder Service Classes resemble JAX-RS/Jersey "Resource" classes (not to be confused with what the Google Guide calls a 
"resource"). 
- Each API Builder Service Class is used to generate a corresponding JAX-RS/Jersey Resource instance and each method
of the API Builder Service Class is used to generate a corresponding JAX-RS/Jersey Resource Method in that instance. 
- API Builder HTTP method annotations are converted to JAX-RS/Jersey counterparts
- `@Produces` and `@Consumes` annotations are generated 
- `@ApiParameter` annotations apply the JAX-RS/Jersey query/header values to the corresponding API Builder instances:
  - `ApiId` - [see details](resources.md#resource-ids)
  - `ApiFilter` - [see details](filtering.md)
  - `ApiOrderby` - [see details](ordering.md)
  - `ApiPagination` - [see details](pagination.md)
  - `ApiValidateOnly` - [see details](validate-only.md)
  - `ApiModifier` and `ApiHeader` - [see details](modifiers.md)

## Next Steps

- See [Resources](resources.md) - details on what constitutes an API Builder resource/model
- See [Methods](methods.md) - details on how to create API Builder methods/operations
- See [URIs](uris.md) - how URIs are automatically generated and their format
- See [Verification and Enforcement](enforcement.md) - API Builder's verification and enforcement of methods, resources, etc.
- See [Mapping](mapping.md) - learn how to create and manage external and internal versions of models, IDs, etc.
