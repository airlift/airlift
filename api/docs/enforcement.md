[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Verification and Enforcement

## Validations

Complete and deep validation of resources and methods is done during the initialization stage of the application. Any non-conforming resource, method,
etc. will cause the application to abort. Note: all errors are reported (i.e. the first error does not abort processing).

### Resource Rules

- Must be Java a `record`
- Fields may only be:
  - _Basic Types_
    - `boolean`
    - `int`
    - `long`
    - `double`
    - `String`
    - `Instant`
    - `LocalDate`
    - `BigDecimal`
    - `UUID`
    - Enumerations
    - Other API resources
    - `ApiId` subclasses
  - `Optional` of basic types 
  - `ApiResourceVersion`
  - Collections (List, Set, etc.) may only be `? extends Collection<String>`, `? extends Collection<? extends ApiId>`, `? extends Collection<? extends Enum>` or `? extends Collection<? is a resource>`.
  - `Map<String, String>`
- Must be annotated with `@ApiResource` - `@ApiResource` requires both a resource name and a publicly displayable description
- Each component/field of the record must have an `@ApiDescription` annotation (except `ApiResourceVersion`) with a publicly displayable description of the component/field
- Components/fields that cannot be updated should be annotated with `@ApiReadOnly`
- `ApiResourceVersion` fields must be named `syncToken` and are implicitly read only
- `ApiId`: 
  - Fields must be named to match the associated resource. E.g. if the `ApiId` is for a resource named `foo` the field must be named `fooId`.
  - Collections of `ApiId` must end with the id name plus `s`. For example: `myResourceIds`.
  - Optionals of `ApiId` must end with the id name. For example: `optionalResourceId`.
- Enumerations must be capitalized camel case
- Resource names must be unique for generating the [OpenApi](openapi.md) spec. By default, the
`@ApiResource` name attribute is used. If this value is not unique, use the `openApiAlternateName` attribute to 
disambiguate.
- Resources can contain recursive references to themselves or other resources only when the reference is part of a collection 

### Method Rules

- All method type annotations require a `description` attribute which provides user displayable
  documentation for the method
- All method type annotations have an optional attribute `responses` for specifying the list
  of possible additional responses it might return. All standard responses for success and most
  failures are added automatically.
- Methods can have standard JAX-RS `@Context`/`@Suspended` parameters
- Parameters must be annotated with `@ApiParameter` - all other annotations are invalid (other than `@Context` or`@Suspended`)
- Some methods can have a single un-annotated parameter that represents the request body. This resource can optionally be wrapped in `ApiMultiPartForm` to enable multipart uploads (see [Multi-part Uploads](multipart.md) for details).
- Returned resources must either be annotated with `@ApiReadOnly` or have an `ApiResourceVersion` and an `ApiId` that matches the resource name
- Update methods can optionally have either one `ApiPatch` parameter which makes the request type PATCH (see [Patching](patch.md) for details)
- For methods that use there are additional rules for request body resources
  - Normal resource rules apply except for nested resources
  - Nested resources in request bodies must be annotated with `@ApiReadOnly`
  - Collections that contain resources must be annotated with `@ApiReadOnly`
- `@ApiGet` methods _may_ return a [`ApiStreamResponse`](streaming.md) instead of a resource to stream responses
- Request bodies for Update must have an `ApiResourceVersion` and an `ApiId` that matches the resource name
- Request bodies for Create cannot have an `ApiResourceVersion`

#### Rules Per Method Type

| Type       | Request Body | Result                  | Pagination | ApiFilter | ApiModifier | ApiHeader | ApiValidateOnly |
|------------|--------------|-------------------------|------------|-----------|-------------|-----------|-----------------|
| **GET**    | ➖            | Single resource         | ➖         | ✔️+       | ✔️+          | ✔️+       | ➖              |
| **LIST**   | ➖            | List of resources       | ✔️          | ✔️+       | ✔️+          | ✔️+       | ➖              |
| **CREATE** | ✔️           | Void or single resource | ➖         | ➖       | ✔️+          | ✔️+       | ✔️              |
| **DELETE** | ➖            | Void or single resource | ➖         | ✔️+        | ✔️+          | ✔️+       | ✔️              |
| **UPDATE** | ✔️           | Void or single resource | ➖         | ✔️+       | ✔️+          | ✔️+       | ✔️              |

- ➖ _Not Allowed_
- ✔️ _1 Allowed_
- ✔️+ _Multiple Allowed_ 

## API Compatibility Checks

Use the `ApiCompatibilityUtil` to generate compatibility hash files. They are used to ensure that a public API
does not change its signature. It also ensures that public APIs don't get removed. It works as follows:

- All configured API services are validated using `ApiCompatibilityUtil`
- If an existing compatibility file is found its hash is verified and an error is generated if it doesn't match
- If there are any compatibility files that no longer appear to match configured API services errors are generated
- If a compatibility file isn't found one of two things happens:
    - if the system property `API_COMPATIBILITY_CREATION_ENABLED` is `true` a new compatibility file is generated and an error about this
      is generated. The new compatibility file must be added to the git repo manually.
    - if the system property `API_COMPATIBILITY_CREATION_ENABLED` is `false` or undefined an error is generated but no file is created
