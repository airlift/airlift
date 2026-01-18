# Why Airlift API Builder

This document analyzes the benefits of adopting Airlift's API Builder framework versus the traditional JAX-RS + Swagger approach, with concrete examples from real-world codebases.

## Executive Summary

Many projects use **JAX-RS + Swagger annotations** to build and document REST APIs. After analyzing typical Airlift-based codebases, we've identified opportunities where **Airlift API Builder** can reduce boilerplate, enforce consistency, and prevent common issues.

| Metric | JAX-RS + Swagger | Airlift API Builder |
|--------|------------------|---------------------|
| Annotations per CRUD method | 3-4 | 1 |
| OpenAPI documentation coverage | Manual (often incomplete) | 100% (automatic) |
| Runtime OpenAPI endpoint | Requires additional setup | `/public/openapi/v1/json` |
| Pagination implementation | Manual, ad-hoc | Built-in, standardized |
| Response code consistency | Developer discretion | Framework-enforced |

## Traditional JAX-RS + Swagger Approach

### Typical Resource Implementation

REST resources using standard JAX-RS annotations with Swagger v3 for OpenAPI generation:

```java
// Traditional approach: RoleResource.java
@Path("/api/v1/roles")
@Produces(APPLICATION_JSON)
@ResourceSecurity(AUTHENTICATED_USER)
public class RoleResource
{
    @GET
    @ApiResponse(responseCode = "200", content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = Role.class))))
    public List<Role> listAll(@Context UserInfo userInfo)
    {
        return roleApi.listAll(userInfo);
    }

    @GET
    @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = Role.class))})
    @Path("{roleId}")
    public Response getById(@Context UserInfo userInfo, @PathParam("roleId") UUID roleId)
    {
        return optionalResponse(roleApi.getById(userInfo, roleId));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    public Role insert(@Context UserInfo userInfo, RoleCreation role)
    {
        return roleApi.insert(userInfo, role.name());
    }

    @DELETE
    @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = Boolean.class))})
    @Path("{roleId}")
    public Response delete(@Context UserInfo userInfo, @PathParam("roleId") UUID roleId)
    {
        boolean deleted = roleApi.delete(userInfo, roleId);
        return (deleted ? Response.ok() : Response.status(NOT_FOUND.getStatusCode())).build();
    }

    // Helper method needed for Optional handling
    public static <T> Response optionalResponse(Optional<T> value)
    {
        if (value.isEmpty()) {
            return Response.status(NOT_FOUND.getStatusCode()).build();
        }
        return Response.ok(value.orElseThrow(), APPLICATION_JSON).build();
    }
}
```

### Annotation Overhead

For a single GET-by-ID method, the traditional approach requires:

```java
@GET                                           // HTTP verb
@Path("{roleId}")                              // Path template
@ApiResponse(responseCode = "200",             // OpenAPI response code
    content = {@Content(                       // OpenAPI content type
        schema = @Schema(                      // OpenAPI schema
            implementation = Role.class))})   // Schema class reference
public Response getById(
    @Context UserInfo userInfo,
    @PathParam("roleId") UUID roleId)          // Path parameter binding
```

**6 annotations** for one endpoint, plus manual `Response` building for Optional handling.

## Airlift API Builder Equivalent

The same resource with Airlift API Builder:

```java
@ResourceSecurity(AUTHENTICATED_USER)
public class RoleResource
{
    @ApiList(description = "...")
    public List<Role> listAll(@Context UserInfo userInfo)
    {
        return roleApi.listAll(userInfo);
    }

    @ApiGet(description = "...")
    public Role getById(@Context UserInfo userInfo, @ApiParameter RoleId roleId)
    {
        return roleApi.getById(userInfo, roleId)
                .orElseThrow(NotFoundException::new);
    }

    @ApiCreate(description = "...")
    public Role insert(@Context UserInfo userInfo, RoleCreation role)
    {
        return roleApi.insert(userInfo, role.name());
    }

    @ApiDelete(description = "...")
    public void delete(@Context UserInfo userInfo, @ApiParameter RoleId roleId)
    {
        if (!roleApi.delete(userInfo, roleId)) {
            throw new NotFoundException();
        }
    }

    // Custom operations for non-standard patterns
    @ApiCustom(type = LIST, verb = "list", description = "...")
    public List<Role> listDeleted(@Context UserInfo userInfo)
    {
        return roleApi.listDeleted(userInfo);
    }

    @ApiCustom(type = UPDATE, verb = "assign", description = "...")
    public void assignUserToRole(
            @Context UserInfo userInfo,
            @ApiParameter RoleId roleId,
            @ApiParameter UserId userId)
    {
        roleApi.assignUserToRole(userInfo, userId, roleId);
    }
}
```

**Key differences:**
- `@ApiGet` replaces `@GET` + `@Path` + `@ApiResponse` + `@Content` + `@Schema`
- `@ApiParameter` replaces `@PathParam` with convention-based path generation
- No `optionalResponse()` helper needed
- OpenAPI generated automatically from method signatures

## Detailed Benefits

### 1. Reduced Annotation Burden

| Operation | JAX-RS + Swagger | Airlift API Builder | Reduction |
|-----------|-----------------|---------------------|-----------|
| List | 3 annotations | 1 (`@ApiList`) | 67% |
| Get | 4-5 annotations | 1 (`@ApiGet`) | 75-80% |
| Create | 3-4 annotations | 1 (`@ApiCreate`) | 67-75% |
| Update | 3-4 annotations | 1 (`@ApiUpdate`) | 67-75% |
| Delete | 3-4 annotations | 1 (`@ApiDelete`) | 67-75% |

For a typical codebase with 80+ endpoint methods, this represents approximately **200+ fewer annotations** to maintain.

### 2. Automatic OpenAPI Generation

**Traditional approach:** OpenAPI is generated at build time via `swagger-maven-plugin`. No runtime endpoint exists without additional configuration.

**With Airlift API Builder:**
- Runtime endpoint at `/public/openapi/v1/json`
- Interactive UI at `/public-api`
- Always in sync with code (no stale documentation)
- No build plugin configuration needed

### 3. Consistent HTTP Status Codes

Traditional codebases often have inconsistencies:

```java
// Issue 1: POST returns 200 instead of 201 Created
@POST
@ApiResponse(responseCode = "200", useReturnTypeSchema = true)
public Role insert(...) { ... }

// Issue 2: DELETE returns 200 instead of 204 No Content
@DELETE
@ApiResponse(responseCode = "200", ...)
public Response delete(...) { ... }

// Issue 3: Mixed return types for same pattern
public Response getById(...)      // Returns Response wrapper
public Role insert(...)           // Returns entity directly
```

**Airlift API Builder enforces:**
- `@ApiCreate` → 201 Created
- `@ApiDelete` → 204 No Content
- `@ApiGet` with missing resource → 404 Not Found
- `@ApiList` → 200 OK with array
- `@ApiUpdate` → 200 OK with updated entity

### 4. Built-in Pagination

Traditional approach requires manual implementation:

```java
// Manual pagination - must implement yourself
@GET
@ApiResponse(...)
public PaginatedResponse<Role> listAll(
        @QueryParam("pageToken") String pageToken,
        @QueryParam("pageSize") @DefaultValue("50") int pageSize)
{
    // Manual pagination logic
    // Manual next page token generation
    // Manual response wrapping
}
```

**With Airlift API Builder:**

```java
@ApiList(description = "...")
public ApiPaginatedResult<Role> listAll(
        @Context UserInfo userInfo,
        @ApiParameter ApiPagination pagination)
{
    return roleApi.listAllPaginated(userInfo, pagination);
}
```

The framework handles:
- `pageToken` and `pageSize` query parameters via the `ApiPagination` record
- Consistent `nextPageToken` in response
- Standardized pagination envelope across all endpoints

### 5. Type-Safe Filtering and Ordering

```java
@ApiList(description = "...")
public ApiPaginatedResult<Role> listRoles(
        @ApiParameter ApiPagination pagination,
        @ApiParameter ApiFilter nameFilter,
        @ApiParameter ApiFilter createdAfter,
        @ApiParameter ApiOrderBy orderBy)
{
    return roleApi.list(nameFilter, createdAfter.map(Instant::new), orderBy);
}
```

Benefits:
- Filter names derived from parameter names
- Type-checked at compile time
- Automatically documented in OpenAPI
- Consistent query parameter naming across all resources

### 6. Validation Dry-Run Support

```java
@ApiCreate(description = "...")
public Role create(RoleCreation input, @ApiParameter ApiValidateOnly validateOnly)
{
    return roleApi.create(input, validateOnly.requested());
}
```

The `ApiValidateOnly` record enables `?validateOnly=true` query parameter support. Clients can validate requests without side effects - useful for form validation UX.

## Problems API Builder Prevents

### Problem 1: Undocumented Endpoints

With JAX-RS + Swagger, functionality and documentation are separate annotation layers:

```java
@GET                    // Required - makes endpoint work
@Path("{roleId}")       // Required - makes endpoint work
@ApiResponse(...)       // Optional - documents endpoint (easily forgotten)
public Response getById(...) { }
```

The endpoint works without `@ApiResponse`, so developers add the JAX-RS annotations to make it functional, then forget (or skip) the Swagger annotations. This is why 15-20% of endpoints in typical codebases lack documentation.

**With Airlift API Builder:** A single annotation serves both purposes:

```java
@ApiGet(description = "Return the role with the given ID")    // Required - makes endpoint work AND documents it
public Role getById(...) { }
```

The `@ApiGet` annotation is required for the endpoint to exist. Documentation is a byproduct of defining the endpoint - you can't have one without the other. If an endpoint works, it's documented.

### Problem 2: Schema Drift

```java
// Schema can drift from actual return type
@ApiResponse(content = @Content(schema = @Schema(implementation = Role.class)))
public Response getById(...)  // Returns Response, not Role!
```

**With Airlift API Builder:** Schema inferred from method signature. Cannot drift.

### Problem 3: Inconsistent Optional Handling

Traditional codebases often use multiple patterns:

```java
// Pattern 1: Helper method with Response wrapper
return optionalResponse(roleApi.getById(userInfo, roleId));

// Pattern 2: Direct throw
return roleApi.getById(userInfo, roleId)
        .orElseThrow(NotFoundException::new);

// Pattern 3: Manual status check
boolean deleted = roleApi.delete(userInfo, roleId);
return (deleted ? Response.ok() : Response.status(NOT_FOUND)).build();
```

**With Airlift API Builder:** One pattern - return the type, framework handles 404.

### Problem 4: No Runtime API Discovery

Traditional approach requires:
1. Build the project to generate OpenAPI JSON
2. Find the generated file in `target/api-spec/`
3. Use external tools to view it

**With Airlift API Builder:** Browse to `/public-api` in any running instance.

## Java Records as API Resources

Airlift API Builder leverages Java records to define user-visible "resources" in the [Google Cloud API Design Guide](https://cloud.google.com/apis/design) sense. Records provide:

**Immutability:** Resources are immutable data structures, matching the API design principle that resources represent state at a point in time.

**Automatic serialization:** Records work seamlessly with Jackson for JSON serialization/deserialization.

**Concise definitions:** A resource that would require a class with constructors, getters, equals, hashCode, and toString becomes a single line:

```java
@ApiResource(name = "role", ...)
public record Role(@ApiDescription("...") RoleId roleId, @ApiDescription("...") String name, @ApiDescription("...") Instant createdAt) {}

@ApiResource(name = "roleCreation", ...)
public record RoleCreation(@ApiDescription("...") String name) {}
```

These records serve as the contract between client and server, automatically reflected in the OpenAPI schema.

## Conclusion

Airlift API Builder offers meaningful improvements over traditional JAX-RS + Swagger:

- **60-75% reduction** in annotation boilerplate
- **100% automatic** OpenAPI documentation
- **Framework-enforced** consistency (status codes, pagination, filtering)
- **Runtime API discovery** at `/public-api`
- **Prevention** of common issues (schema drift, undocumented endpoints, inconsistent patterns)
- **Java records** for clean, type-safe resource definitions

The framework aligns well with existing Airlift infrastructure investments and follows the Google Cloud API Design Guide principles.

## Design Philosophy

Airlift API Builder follows the [Google Cloud API Design Guide](https://cloud.google.com/apis/design) principles:

- **Resources** - Java records validated against naming and structural conventions
- **Methods** - Standardized operations (List, Get, Create, Update, Delete) replacing raw HTTP verbs
- **URIs** - Automatically generated paths conforming to specification guidelines

This opinionated approach trades flexibility for consistency, which pays dividends in larger codebases and teams.
