# Airlift HTTP Client Generator

An [OpenAPI Generator](https://openapi-generator.tech/) that produces Java clients using Airlift's `HttpClient` library. The generated code follows the same patterns used in [Trino](https://github.com/trinodb/trino)'s handwritten HTTP clients.

## Design Goals

The generated clients aim to match the structure and style of Trino's handwritten Airlift HTTP clients, such as `OpenAiClient`, `AnthropicClient`, and `OpaHttpClient`. Specifically:

- **Direct `HttpClient` usage** — no intermediary `ApiClient` layer. Each generated client class holds an `HttpClient` and `URI` directly, matching how Trino structures its clients.
- **`HttpUriBuilder`** for URI construction — uses `uriBuilderFrom(baseUri).appendPath(...)` with `.addParameter()` for query parameters.
- **Static final `JsonCodec` fields** — deduplicated codecs declared as `private static final` with UPPER_SNAKE_CASE names (e.g., `PET_CODEC`), matching Trino's codec declaration style.
- **Airlift request builders** — uses `preparePost()`, `prepareGet()`, etc. with fluent `.setUri()`, `.setHeader()`, `.setBodyGenerator()` chaining.
- **`JsonResponseHandler` and `StatusResponseHandler`** — uses `createJsonResponseHandler(codec)` for responses with bodies and `createStatusResponseHandler()` for void responses.
- **Retry with exponential backoff** — all `httpClient.execute()` calls are wrapped in a `RetryPolicy` that retries transient failures with configurable exponential backoff. Non-retryable errors fail immediately.
- **Error classification** — distinguishes retryable errors (network exceptions like `SocketException`, `SocketTimeoutException`, `ConnectException`, and HTTP status codes 429, 502, 503, 504) from non-retryable errors that fail fast.
- **Retry-After header support** — when a server returns a `Retry-After` header (common with 429 Too Many Requests), the retry delay uses the server-suggested value instead of exponential backoff.
- **Bearer token authentication** — when the OpenAPI spec defines a `bearerAuth` security scheme, the generated client adds `Authorization: Bearer` headers, stores the API key as a field, and exposes it through the config class.
- **Guice integration** — generates a `Module`, `Config`, and `@BindingAnnotation` annotation following Airlift's dependency injection patterns.
- **Java records for models** — generates immutable record types with `@JsonProperty` annotations.
- **Minimal imports** — only imports the specific static methods and types actually used by the generated code.

## Usage

### Generator Name

```
airlift-http-client
```

### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `projectName` | Name used for config prefix and module naming (e.g., `petstore`, `open-ai`) | `api` |
| `clientName` | Override PascalCase name for generated classes (e.g., `OpenAi` instead of auto-derived `Openai`) | Derived from `projectName` |
| `apiPackage` | Java package for generated client classes | — |
| `modelPackage` | Java package for generated model classes | — |
| `invokerPackage` | Java package for supporting files (module, config, annotation, exception) | Derived from `apiPackage` |
| `javaVersion` | JDK level used in the generated `pom.xml` | Bundled default |
| `airbaseVersion` | Airbase parent version used in the generated `pom.xml` | Bundled default |
| `airliftVersion` | Airlift BOM version imported by the generated `pom.xml` | Bundled default |

### Example: Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.14.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <generatorName>airlift-http-client</generatorName>
                <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
                <configOptions>
                    <projectName>petstore</projectName>
                    <apiPackage>com.example.api</apiPackage>
                    <modelPackage>com.example.model</modelPackage>
                    <invokerPackage>com.example</invokerPackage>
                </configOptions>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>http-client-generator</artifactId>
            <version>${dep.airlift.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

### Example: Generated Client

Given a Petstore OpenAPI spec with a POST endpoint:

```yaml
paths:
  /pets:
    post:
      operationId: createPet
      tags:
        - pets
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
      responses:
        '201':
          description: Pet created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
components:
  schemas:
    Pet:
      type: object
```

The generator produces a client that matches Trino's style:

```java
public class PetsClient
{
    private final HttpClient httpClient;
    private final URI baseUri;
    private final RetryPolicy retryPolicy;

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final JsonCodec<Pet> PET_CODEC = jsonCodec(Pet.class);

    @Inject
    public PetsClient(@ForPetstore HttpClient httpClient, PetstoreClientConfig config)
    {
        this(httpClient, config.getBaseUri(),
                new RetryPolicy(config.getMaxRetries(), config.getRetryInitialDelayMs(), config.getRetryMaxDelayMs()));
    }

    public Pet createPet(Pet pet)
    {
        URI uri = uriBuilderFrom(baseUri)
                .appendPath("/pets")
                .build();

        Request request = preparePost()
                .setUri(uri)
                .setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE)
                .setBodyGenerator(jsonBodyGenerator(PET_CODEC, pet))
                .build();

        return retryPolicy.execute("createPet", uri, () ->
                httpClient.execute(request, createJsonResponseHandler(PET_CODEC)));
    }
}
```

### Generated Files

For a project named `petstore` with a tag `pets`, the generator produces:

| File | Description |
|------|-------------|
| `api/PetsClient.java` | HTTP client class with typed methods for each operation |
| `model/*.java` | Java records for request/response schemas |
| `PetstoreClientModule.java` | Guice module that binds the HTTP client, config, and client classes |
| `PetstoreClientConfig.java` | Airlift `@Config` class with `baseUri`, retry settings (and `apiKey` when auth is present) |
| `ForPetstore.java` | Guice `@BindingAnnotation` for the HTTP client |
| `RetryPolicy.java` | Retry with exponential backoff, error classification, and Retry-After support |
| `ApiException.java` | Runtime exception for HTTP call failures |

### PascalCase Naming

The `clientName` is derived from `projectName` by splitting on `-`, `_`, and spaces. For single-word names without separators (e.g., `openai`), the auto-derivation produces `Openai`. Use the `clientName` property to override:

```xml
<configOptions>
    <projectName>openai</projectName>
    <clientName>OpenAi</clientName>
</configOptions>
```

Or use separators in `projectName`:

```xml
<configOptions>
    <projectName>open-ai</projectName>
    <!-- Produces: OpenAi, ForOpenAi, OpenAiClientConfig -->
</configOptions>
```

### Resiliency

Every generated client includes a `RetryPolicy` that provides retry with exponential backoff, error classification, and Retry-After header support. The retry behavior is fully configurable via Airlift `@Config` properties.

#### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `{prefix}.max-retries` | Maximum number of retry attempts | `3` |
| `{prefix}.retry-initial-delay-ms` | Initial delay before first retry (milliseconds) | `100` |
| `{prefix}.retry-max-delay-ms` | Maximum delay between retries (milliseconds) | `1000` |

Where `{prefix}` is derived from `projectName` (e.g., `petstore.max-retries` for project name `petstore`).

#### Error Classification

Only transient errors are retried. Non-retryable errors fail immediately without delay:

| Error Type | Retryable | Examples |
|------------|-----------|----------|
| Network errors | Yes | `SocketException`, `SocketTimeoutException`, `ConnectException` |
| Server errors | Yes | HTTP 429 (Too Many Requests), 502, 503, 504 |
| Client errors | No | HTTP 400, 401, 403, 404, 409 |
| Other exceptions | No | `IllegalArgumentException`, `NullPointerException` |

#### Retry-After Header

When a server returns a `Retry-After` header (common with HTTP 429 responses), the retry delay uses the server-suggested value (in seconds) instead of exponential backoff, capped at `retry-max-delay-ms`.

#### Disabling Retries

To disable retries entirely, set `max-retries` to `0`, or use `RetryPolicy.disabled()` programmatically:

```java
var client = new PetsClient(httpClient, baseUri, RetryPolicy.disabled());
```

### Security Schemes

The generator supports bearer token authentication from OpenAPI `securitySchemes`:

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
security:
  - bearerAuth: []
```

When present, the generated code:
- Adds `private final String apiKey` to the client class
- Adds `.setHeader(AUTHORIZATION, "Bearer " + apiKey)` to authenticated requests
- Adds `getApiKey()` / `setApiKey()` to the config class with `@Config("projectname.api-key")`

API key authentication in headers is also supported:

```yaml
components:
  securitySchemes:
    apiKey:
      type: apiKey
      in: header
      name: X-API-Key
```

This generates `.setHeader("X-API-Key", apiKey)` on authenticated requests.
