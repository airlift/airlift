# Airlift HTTP Client Generator

An [OpenAPI Generator](https://openapi-generator.tech/) that produces Java clients using Airlift's `HttpClient` library. The generated code follows the same patterns used in [Trino](https://github.com/trinodb/trino)'s handwritten HTTP clients.

## Design Goals

The generated clients aim to match the structure and style of Trino's handwritten Airlift HTTP clients, such as `OpenAiClient`, `AnthropicClient`, and `OpaHttpClient`. Specifically:

- **Direct `HttpClient` usage** — no intermediary `ApiClient` layer. Each generated client class holds an `HttpClient` and `URI` directly, matching how Trino structures its clients.
- **`HttpUriBuilder`** for URI construction — uses `uriBuilderFrom(baseUri).appendPath(...)` with `.addParameter()` for query parameters.
- **Static final `JsonCodec` fields** — deduplicated codecs declared as `private static final` with UPPER_SNAKE_CASE names (e.g., `PET_CODEC`), matching Trino's codec declaration style.
- **Airlift request builders** — uses `preparePost()`, `prepareGet()`, etc. with fluent `.setUri()`, `.setHeader()`, `.setBodyGenerator()` chaining.
- **`JsonResponseHandler` and `StatusResponseHandler`** — uses handlers with explicit success codes when the contract lists them, and handlers accepting any successful status when the contract declares a `2XX` response range.
- **Retry with exponential backoff** — generated calls use a `RetryPolicy`, but transport retries are limited to safe/idempotent operations: `GET`, `HEAD`, `OPTIONS`, `PUT`, and `DELETE`, plus `POST` or `PATCH` with a nonblank idempotency key. Non-retryable errors fail immediately.
- **Error classification** — distinguishes retryable errors (network exceptions like `SocketException`, `SocketTimeoutException`, `ConnectException`, and HTTP status codes 429, 502, 503, 504) from non-retryable errors that fail fast.
- **Retry-After header support** — when a server returns a `Retry-After` header (common with 429 Too Many Requests), the retry delay uses the server-suggested value instead of exponential backoff.
- **Named authentication credentials** — generated clients hold `HttpClientCredentials`, select a satisfiable OpenAPI security alternative, and apply bearer, Basic, or header API-key credentials by scheme name. Bearer tokens are resolved from their provider for each attempt.
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
                new RetryPolicy(config.getMaxRetries(), config.getRetryInitialDelay(), config.getRetryMaxDelay()));
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

        return retryPolicy.execute("createPet", "POST", null, exception -> new ApiException("createPet", exception), () ->
                httpClient.execute(request, createSafeJsonResponseHandler(PET_CODEC, 201)));
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
| `PetstoreClientConfig.java` | Airlift `@Config` class with `baseUri`, retry settings, and configured named credentials when authentication is present |
| `ForPetstore.java` | Guice `@BindingAnnotation` for the HTTP client |
| `RetryPolicy.java` | Retry with exponential backoff, error classification, and Retry-After support |
| `PetstoreOAuth2.java` | Token provider factories, generated when the contract declares an OAuth2 client-credentials scheme |
| `ApiException.java` | Structured failure carrying the operation, request method and URI, status, headers, bounded response body, and decoded error model |

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

Every generated client includes a `RetryPolicy` that provides retry with exponential backoff, error classification, and Retry-After header support. Transport retries apply only to safe/idempotent operations: `GET`, `HEAD`, `OPTIONS`, `PUT`, and `DELETE`, plus `POST` or `PATCH` with a nonblank idempotency key. The retry behavior is fully configurable via Airlift `@Config` properties.

#### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `{prefix}.max-retries` | Maximum number of retry attempts | `3` |
| `{prefix}.retry-initial-delay` | Initial delay before the first retry | `100ms` |
| `{prefix}.retry-max-delay` | Maximum delay between retries | `1s` |

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

When a server returns a `Retry-After` header (common with HTTP 429 responses), the retry delay uses the server-suggested value (in seconds) instead of exponential backoff, capped at `retry-max-delay`.

#### Idempotent Mutations

`POST` and `PATCH` operations are retried only when the contract marks them idempotent. API Builder emits the `x-airlift-idempotency` extension naming the idempotency header; the generated method takes that key as a parameter, sends it as the header, and permits transport retries when the key is nonblank.

#### Disabling Retries

To disable retries entirely, set `max-retries` to `0`, or use `RetryPolicy.disabled()` programmatically:

```java
var client = new PetsClient(httpClient, baseUri, RetryPolicy.disabled());
```

### Failures

Every failed call throws the generated `ApiException`. Its message names the operation and, for HTTP failures, the status code; headers and response bodies never appear in the message. The exception exposes:

| Accessor | Contents |
|----------|----------|
| `getOperationName()`, `getRequestMethod()`, `getRequestUri()` | The failed operation and its request, with the URI query string removed |
| `getStatusCode()`, `getHeaders()`, `getContentType()` | The response status and headers, or status `0` when no response was received |
| `getResponseBody()`, `getResponseBodyBytes()`, `isResponseBodyTruncated()` | The response body, retained up to a bounded size |
| `getError()`, `getError(Class)` | The decoded error model when the contract declares one for that status |

Transport failures, interrupted retries, and bearer token provider failures are wrapped in the same exception with the original cause attached.

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

Basic authentication (`type: http`, `scheme: basic`) is supported alongside bearer and header API-key schemes. When present, the generated code:

- Stores `HttpClientCredentials` in the client and selects a configured security alternative for each operation.
- Resolves bearer tokens through `BearerTokenProvider` for each attempt and applies them through the named credentials.
- Exposes a configuration setter for each supported scheme, named after the scheme.

API key authentication in headers is also supported:

```yaml
components:
  securitySchemes:
    apiKey:
      type: apiKey
      in: header
      name: X-API-Key
```

This adds a named header API-key credential and applies it to authenticated requests.

#### Credential Configuration

| Scheme | Properties |
|--------|------------|
| Bearer | `{prefix}.{scheme}.token` |
| Basic | `{prefix}.{scheme}.username` and `{prefix}.{scheme}.password`, configured together |
| Header API key | `{prefix}.{scheme}.api-key` |
| OAuth2 client credentials | `{prefix}.{scheme}.token` for a static token; build a refreshing provider with the generated `{ClientName}OAuth2` factory |

`{scheme}` is the hyphenated scheme name (`serviceBearer` becomes `service-bearer`). Every credential is optional; the client selects a security alternative that the configured credentials satisfy and fails with `IllegalStateException` when none does. Only schemes referenced by some security requirement shape the generated client and configuration.

#### OAuth2 Client Credentials

An `oauth2` scheme with a `clientCredentials` flow generates a `{ClientName}OAuth2` factory:

```java
ClientCredentialsTokenProvider provider = PetstoreOAuth2.createServiceOAuthTokenProvider(httpClient, baseUri, clientId, clientSecret);
HttpClientCredentials credentials = HttpClientCredentials.builder().bearerToken("serviceOAuth", provider).build();
var client = new PetsClient(httpClient, baseUri, credentials);
```

The provider acquires tokens from the contract's `tokenUrl`, resolved against the client base URI, using the required scopes and the `x-airlift-token-endpoint-authentication-method` emitted by API Builder. It caches tokens until their skewed expiry, coalesces concurrent refreshes, and retries the token endpoint independently of API retries. One provider can be shared by clients generated from different contracts.

#### Bearer Token Refresh

Bearer tokens come from a `BearerTokenProvider`. When a request fails with `401` carrying a `WWW-Authenticate: Bearer` challenge, the client asks the provider to refresh the rejected token once and repeats the request with the new token. Refresh has its own one-attempt budget and does not consume transport retries.
