/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client.generator;

import com.google.inject.spi.Message;
import io.airlift.configuration.ConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class AirliftHttpClientCodegenIntegrationTest
{
    @Test
    void testGeneratesNamedAuthenticationAlternatives(@TempDir Path outputPath)
            throws Exception
    {
        generate("mixed-authentication.yaml", outputPath);

        Path clientFile = outputPath.resolve("src/main/java/org/openapitools/client/api/MixedClient.java");
        assertThat(clientFile).exists();
        String clientContent = Files.readString(clientFile);
        assertThat(clientContent)
                .contains("private final ApiCredentials credentials")
                .doesNotContain("private final String apiKey")
                .containsSubsequence(
                        "if (credentials.hasServiceBasic() && credentials.hasServiceKey())",
                        "else if (credentials.hasServiceBearer())")
                .contains("credentials.applyServiceBasic(requestBuilder)")
                .contains("credentials.applyServiceKey(requestBuilder)")
                .contains("requestBuilder.setHeader(AUTHORIZATION, \"Bearer \" + requireNonNull(bearerToken, \"bearerToken is null\"))")
                .contains("credentials.getServiceOAuthTokenProvider()")
                .doesNotContain("if (true)")
                .contains("No configured credentials satisfy authentication for operation")
                .doesNotContain("query-secret", "cookie-secret");

        Path configFile = outputPath.resolve("src/main/java/org/openapitools/client/ApiClientConfig.java");
        assertThat(configFile).exists();
        String configContent = Files.readString(configFile);
        assertThat(configContent)
                .contains("setServiceBearerToken")
                .contains("setServiceBasicUsername")
                .contains("setServiceBasicPassword")
                .contains("setServiceKeyApiKey")
                .contains("setServiceOAuthToken")
                .contains("ApiCredentials getCredentials()")
                .doesNotContain("public String getApiKey()");

        // one typed method per scheme, so a misspelled scheme name does not compile
        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/ApiCredentials.java")))
                .contains("public Builder withServiceBearer(BearerTokenProvider tokenProvider)")
                .contains("public Builder withServiceBasic(String username, String password)")
                .contains("public Builder withServiceKey(String apiKey)")
                .contains(".setHeader(\"X-Service-Key\", requireConfigured(serviceKeyApiKey, \"serviceKey\"))");

        Path oauthFactory = outputPath.resolve("src/main/java/org/openapitools/client/ApiOAuth2.java");
        assertThat(oauthFactory).exists();
        assertThat(Files.readString(oauthFactory))
                .contains("public static ClientCredentialsTokenProvider createServiceOAuthTokenProvider(")
                .contains("requireNonNull(baseUri, \"baseUri is null\").resolve(\"/oauth/token?tenant=first&audience=api\")")
                .contains(".scope(\"status:read\")")
                .contains(".resource(resource)");

        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testGeneratesNonBearerAuthentication(@TempDir Path outputPath)
            throws Exception
    {
        generate("non-bearer-authentication.yaml", outputPath);

        Path clientFile = outputPath.resolve("src/main/java/org/openapitools/client/api/NonBearerClient.java");
        assertThat(clientFile).exists();
        assertThat(Files.readString(clientFile))
                .contains("credentials.applyServiceBasic(requestBuilder)")
                .contains("credentials.applyServiceKey(requestBuilder)")
                .doesNotContain("authenticationBearerTokenProvider", "NO_BEARER_TOKEN", "AUTHORIZATION");
        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/ApiCredentials.java")))
                .doesNotContain("BearerTokenProvider");

        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testRejectsQueryApiKey(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("query-api-key.yaml", outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key scheme 'queryKey' uses unsupported location 'query'; only header API keys are supported");
    }

    @Test
    void testRejectsInvalidHeaderApiKeyName(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("invalid-header-name.yaml", outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Header API key scheme 'serviceKey' has an invalid header name 'Bad Header'");
    }

    @Test
    void testRejectsCollidingAuthenticationNames(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("colliding-authentication-names.yaml", outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Security scheme names 'service-key' and 'service_key' generate the same Java property 'serviceKey'");
    }

    @Test
    void testRejectsInconsistentOAuthScopes(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("inconsistent-oauth-scopes.yaml", outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Security scheme 'serviceOAuth' is used with inconsistent required scopes: [read] and [write]");
    }

    @Test
    void testGeneratesResponseRanges(@TempDir Path outputPath)
            throws Exception
    {
        generate("response-ranges.yaml", outputPath);

        Path clientFile;
        try (Stream<Path> files = Files.walk(outputPath)) {
            clientFile = files
                    .filter(path -> path.getFileName().toString().equals("DefaultClient.java"))
                    .findFirst()
                    .orElseThrow();
        }
        String clientContent = Files.readString(clientFile);
        assertThat(clientContent)
                .contains("createSuccessfulJsonResponseHandler(ITEM_CODEC)")
                .contains("createSuccessfulStatusResponseHandler()")
                .doesNotContain("createSafeJsonResponseHandler", "createStatusResponseHandler")
                .containsSubsequence(
                        "if (statusCode == 409)",
                        "EXACT_ERROR_CODEC",
                        "if (statusCode >= 400 && statusCode < 500)",
                        "CLIENT_ERROR_CODEC",
                        "if (true)",
                        "DEFAULT_ERROR_CODEC");

        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testSuccessSchemaDefaultIsNotAnErrorType(@TempDir Path outputPath)
            throws Exception
    {
        generate("success-schema-default.yaml", outputPath);

        // a default response without a body decodes nothing, whatever default value the success schema declares
        String client = Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/LabelsClient.java"));
        assertThat(client).contains("public List<String> listLabels()");
        assertThat(client).doesNotContain("unlabeled");
        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testRejectsIdempotencyMetadataWithoutHeader(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("idempotency-missing-header.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("x-airlift-idempotency header 'Idempotency-Key' is not an operation header parameter");
    }

    @Test
    void testIdempotencyExtensionNameIsConfigurable(@TempDir Path outputPath)
            throws Exception
    {
        new DefaultGenerator()
                .opts(new CodegenConfigurator()
                        .setGeneratorName("airlift-http-client")
                        .setInputSpec(getClass().getClassLoader().getResource("custom-idempotency-extension.yaml").getFile())
                        .setOutputDir(outputPath.toString())
                        .addAdditionalProperty(AirliftHttpClientCodegen.IDEMPOTENCY_EXTENSION, "x-my-idempotency")
                        .toClientOptInput())
                .generate();

        String client = Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/JobsClient.java"));
        assertThat(client).contains("retryPolicy.execute(\"createJob\", \"POST\", idempotencyKey, ");
        verifyGeneratedCodeCompiles(outputPath);

        // without the option the declaration is not recognized, so the mutation is not retried
        Path defaultOutputPath = outputPath.resolve("default");
        generate("custom-idempotency-extension.yaml", defaultOutputPath);
        assertThat(Files.readString(defaultOutputPath.resolve("src/main/java/org/openapitools/client/api/JobsClient.java")))
                .contains("retryPolicy.execute(\"createJob\", \"POST\", null, ");
    }

    @Test
    void testRejectsIdempotencyMetadataWithNonStringHeader(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("idempotency-non-string-header.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("x-airlift-idempotency header 'Idempotency-Key' must be a string operation header parameter");
    }

    @Test
    void testGeneratePetstoreClient(@TempDir Path outputPath)
            throws Exception
    {
        String inputSpec = getClass().getClassLoader().getResource("petstore.yaml").getFile();

        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("airlift-http-client")
                .setInputSpec(inputSpec)
                .setOutputDir(outputPath.toString())
                .addAdditionalProperty("projectName", "petstore")
                .addAdditionalProperty("apiPackage", "com.example.api")
                .addAdditionalProperty("modelPackage", "com.example.model")
                .addAdditionalProperty("invokerPackage", "com.example")
                .addAdditionalProperty("groupId", "com.example")
                .addAdditionalProperty("artifactId", "petstore-client")
                .addAdditionalProperty("artifactVersion", "1.0.0");

        ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> generatedFiles = generator.opts(clientOptInput).generate();

        assertThat(generatedFiles).isNotEmpty();

        // Verify client class (named *Client, not *Api)
        Path clientFile = outputPath.resolve("src/main/java/com/example/api/PetsClient.java");
        assertThat(clientFile).exists();
        String clientContent = Files.readString(clientFile);

        // Class structure: direct HttpClient and baseUri fields
        assertThat(clientContent).contains("public class PetsClient");
        assertThat(clientContent).contains("private final HttpClient httpClient");
        assertThat(clientContent).contains("private final URI baseUri");
        assertThat(clientContent).contains("@Inject");

        // No auth for petstore (no security scheme)
        assertThat(clientContent).doesNotContain("private final String apiKey");
        assertThat(clientContent).doesNotContain("AUTHORIZATION");

        // Static final codecs
        assertThat(clientContent).contains("private static final JsonCodec<Pet> PET_CODEC = jsonCodec(Pet.class)");
        assertThat(clientContent).contains("private static final JsonCodec<List<Pet>> PET_ARRAY_CODEC = listJsonCodec(jsonCodec(Pet.class))");
        assertThat(clientContent).contains("private static final JsonCodec<StatusUpdate> STATUS_UPDATE_CODEC = jsonCodec(StatusUpdate.class)");

        // URI building with HttpUriBuilder
        assertThat(clientContent).contains("uriBuilderFrom(baseUri)");
        assertThat(clientContent).contains(".appendPath(");

        // Request building with Airlift static imports
        assertThat(clientContent).contains("preparePost()");
        assertThat(clientContent).contains("prepareGet()");
        assertThat(clientContent).contains("preparePut()");
        assertThat(clientContent).contains("prepareDelete()");
        assertThat(clientContent).contains("preparePatch()");

        // Headers use a local constant instead of Guava MediaType
        assertThat(clientContent).contains("private static final String JSON_CONTENT_TYPE = \"application/json; charset=utf-8\"");
        assertThat(clientContent).contains("setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE)");

        // Retry-wrapped httpClient.execute() calls
        assertThat(clientContent).contains("retryPolicy.execute(");
        assertThat(clientContent).contains("httpClient.execute(request, createSafeJsonResponseHandler(");
        assertThat(clientContent).contains("createStatusResponseHandler(204)");
        assertThat(clientContent).doesNotContain("createSuccessfulJsonResponseHandler", "createSuccessfulStatusResponseHandler");

        // RetryPolicy field and constructor
        assertThat(clientContent).contains("private final RetryPolicy retryPolicy");
        assertThat(clientContent).contains("RetryPolicy.withDefaults()");
        assertThat(clientContent).contains("config.getMaxRetries()");

        // Body generation with jsonBodyGenerator
        assertThat(clientContent).contains("jsonBodyGenerator(PET_CODEC, pet)");
        assertThat(clientContent).contains("jsonBodyGenerator(STATUS_UPDATE_CODEC, statusUpdate)");

        // Query parameter handling via HttpUriBuilder.addParameter
        assertThat(clientContent).contains("uriBuilder.addParameter(");

        // Client methods present
        assertThat(clientContent).contains("listPets");
        assertThat(clientContent).contains("createPet");
        assertThat(clientContent).contains("getPetById");
        assertThat(clientContent).contains("updatePet");
        assertThat(clientContent).contains("deletePet");
        assertThat(clientContent).contains("updatePetStatus");

        // No async variants
        assertThat(clientContent).doesNotContain("ListenableFuture");
        assertThat(clientContent).doesNotContain("Async");

        // Conditional imports: only what's used
        assertThat(clientContent).contains("import static io.airlift.http.client.Request.Builder.prepareGet;");
        assertThat(clientContent).contains("import static io.airlift.http.client.Request.Builder.preparePost;");
        assertThat(clientContent).contains("import static io.airlift.json.JsonCodec.listJsonCodec;");
        assertThat(clientContent).doesNotContain("mapJsonCodec");

        // Verify model classes
        Path petModel = outputPath.resolve("src/main/java/com/example/model/Pet.java");
        assertThat(petModel).exists();
        String petContent = Files.readString(petModel);
        assertThat(petContent).contains("public record Pet");
        assertThat(petContent).contains("@JsonProperty");
        assertThat(petContent).doesNotContain("RecordBuilder");

        // Verify enum model
        Path statusModel = outputPath.resolve("src/main/java/com/example/model/PetStatus.java");
        assertThat(statusModel).exists();
        assertThat(Files.readString(statusModel)).contains("public enum PetStatus");

        // Verify ApiException extends RuntimeException with message+cause constructor
        Path apiException = outputPath.resolve("src/main/java/com/example/ApiException.java");
        assertThat(apiException).exists();
        String exceptionContent = Files.readString(apiException);
        assertThat(exceptionContent).contains("extends RuntimeException");
        assertThat(exceptionContent).contains("String message, Throwable cause");

        // Verify Guice module binds client classes
        Path clientModule = outputPath.resolve("src/main/java/com/example/PetstoreClientModule.java");
        assertThat(clientModule).exists();
        String moduleContent = Files.readString(clientModule);
        assertThat(moduleContent).contains("public class PetstoreClientModule");
        assertThat(moduleContent).contains("implements Module");
        assertThat(moduleContent).contains("httpClientBinder");
        assertThat(moduleContent).contains("PetsClient.class");

        // Verify config class (no @ConfigDescription, no apiKey for petstore)
        Path clientConfig = outputPath.resolve("src/main/java/com/example/PetstoreClientConfig.java");
        assertThat(clientConfig).exists();
        String configContent = Files.readString(clientConfig);
        assertThat(configContent).contains("@Config");
        assertThat(configContent).contains("getBaseUri");
        assertThat(configContent).doesNotContain("@ConfigDescription");
        assertThat(configContent).doesNotContain("apiKey");

        // Verify binding annotation
        Path annotation = outputPath.resolve("src/main/java/com/example/ForPetstore.java");
        assertThat(annotation).exists();
        assertThat(Files.readString(annotation)).contains("@BindingAnnotation");
        assertThat(Files.readString(annotation)).contains("public @interface ForPetstore");

        Path pomFile = outputPath.resolve("pom.xml");
        assertThat(pomFile).exists();
        String pomContent = Files.readString(pomFile);
        assertThat(pomContent).contains("<artifactId>airbase</artifactId>");
        assertThat(pomContent).contains("<artifactId>bom</artifactId>");
        assertThat(pomContent).contains("<artifactId>guava</artifactId>");

        // Verify generated code compiles
        verifyGeneratedCodeCompiles(outputPath);
    }

    private void assertCredentialsOptional(Path classesDir, String configClassName, String configPrefix, String credentialProperty)
            throws Exception
    {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> configClass = classLoader.loadClass(configClassName);
            Map<String, String> baseUriOnly = Map.of(configPrefix + ".base-uri", "https://example.test");
            assertThat(configurationErrors(configClass, baseUriOnly)).isEmpty();

            Map<String, String> withCredential = new java.util.HashMap<>(baseUriOnly);
            withCredential.put(configPrefix + "." + credentialProperty, "secret");
            assertThat(configurationErrors(configClass, withCredential)).isEmpty();
        }
    }

    private static List<Message> configurationErrors(Class<?> configClass, Map<String, String> properties)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        configurationFactory.registerConfigurationClasses(List.of(binder -> configBinder(binder).bindConfig(configClass)));
        return configurationFactory.validateRegisteredConfigurationProvider();
    }

    @Test
    void testGenerateOpenAiClient(@TempDir Path outputPath)
            throws Exception
    {
        String inputSpec = getClass().getClassLoader().getResource("openai-chat.yaml").getFile();

        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("airlift-http-client")
                .setInputSpec(inputSpec)
                .setOutputDir(outputPath.toString())
                .addAdditionalProperty("projectName", "open-ai")
                .addAdditionalProperty("apiPackage", "io.trino.plugin.ai.generated.api")
                .addAdditionalProperty("modelPackage", "io.trino.plugin.ai.generated.model")
                .addAdditionalProperty("invokerPackage", "io.trino.plugin.ai.generated");

        ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> generatedFiles = generator.opts(clientOptInput).generate();

        assertThat(generatedFiles).isNotEmpty();

        // Verify client class matches Trino's OpenAiClient pattern
        Path clientFile = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/api/OpenAiClient.java");
        assertThat(clientFile).exists();
        String clientContent = Files.readString(clientFile);

        // Class structure matches Trino's pattern
        assertThat(clientContent).contains("public class OpenAiClient");
        assertThat(clientContent).contains("private final HttpClient httpClient");
        assertThat(clientContent).contains("private final URI baseUri");
        assertThat(clientContent).contains("private final OpenAiCredentials credentials");
        assertThat(clientContent).doesNotContain("private final String apiKey");

        // Bearer auth: Authorization header
        assertThat(clientContent).contains("requestBuilder.setHeader(AUTHORIZATION, \"Bearer \" + requireNonNull(bearerToken, \"bearerToken is null\"))");

        // credentials are always the generated OpenAiCredentials; no per-scheme convenience constructors
        assertThat(clientContent).contains("config.getCredentials()");
        assertThat(clientContent).contains("public OpenAiClient(HttpClient httpClient, URI baseUri, OpenAiCredentials credentials)");
        assertThat(clientContent).doesNotContain("URI baseUri, String apiKey)", "URI baseUri, BearerTokenProvider bearerTokenProvider)");
        assertThat(clientContent).contains("retryPolicy.execute(\"generateCompletion\", \"POST\", null, authenticationBearerTokenProvider");

        Path bearerTokenProviderFile = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/BearerTokenProvider.java");
        assertThat(bearerTokenProviderFile).doesNotExist();

        // Static codecs matching Trino's naming
        assertThat(clientContent).contains("private static final JsonCodec<ChatRequest> CHAT_REQUEST_CODEC = jsonCodec(ChatRequest.class)");
        assertThat(clientContent).contains("private static final JsonCodec<ChatResponse> CHAT_RESPONSE_CODEC = jsonCodec(ChatResponse.class)");

        // Method name matches Trino's pattern
        assertThat(clientContent).contains("public ChatResponse generateCompletion(ChatRequest chatRequest)");

        // Request building matches Trino's pattern
        assertThat(clientContent).contains("uriBuilderFrom(baseUri)");
        assertThat(clientContent).contains(".appendPath(\"/v1/chat/completions\")");
        assertThat(clientContent).contains("preparePost()");
        assertThat(clientContent).contains("private static final String JSON_CONTENT_TYPE = \"application/json; charset=utf-8\"");
        assertThat(clientContent).contains("setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE)");
        assertThat(clientContent).contains("jsonBodyGenerator(CHAT_REQUEST_CODEC, chatRequest)");
        assertThat(clientContent).contains("httpClient.execute(request, createSafeJsonResponseHandler(CHAT_RESPONSE_CODEC, 200))");
        assertThat(clientContent).doesNotContain("createSuccessfulJsonResponseHandler");

        // Retry-wrapped execution
        assertThat(clientContent).contains("retryPolicy.execute(");
        assertThat(clientContent).contains("private final RetryPolicy retryPolicy");
        assertThat(clientContent).contains("RetryPolicy.withDefaults()");
        assertThat(clientContent).contains("config.getMaxRetries()");

        // Only imports what's needed (POST only, no GET/PUT/DELETE/PATCH)
        assertThat(clientContent).contains("import static io.airlift.http.client.Request.Builder.preparePost;");
        assertThat(clientContent).doesNotContain("prepareGet");
        assertThat(clientContent).doesNotContain("preparePut");
        assertThat(clientContent).doesNotContain("prepareDelete");
        assertThat(clientContent).doesNotContain("preparePatch");
        assertThat(clientContent).doesNotContain("createStatusResponseHandler");
        assertThat(clientContent).doesNotContain("listJsonCodec");
        assertThat(clientContent).doesNotContain("mapJsonCodec");

        // Verify config exposes the bearer token under the scheme name
        Path clientConfig = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/OpenAiClientConfig.java");
        assertThat(clientConfig).exists();
        String configContent = Files.readString(clientConfig);
        assertThat(configContent).contains("getBearerAuthToken");
        assertThat(configContent).contains("setBearerAuthToken");
        assertThat(configContent).contains("@Config(\"openai.bearer-auth.token\")");
        assertThat(configContent).contains("@ConfigSecuritySensitive");
        assertThat(configContent).contains("@Config(\"openai.base-uri\")");
        assertThat(configContent).doesNotContain("getApiKey", "api-key", "@NotNull\n    public String getBearerAuthToken()");

        // Verify binding annotation uses correct PascalCase
        Path annotation = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/ForOpenAi.java");
        assertThat(annotation).exists();
        assertThat(Files.readString(annotation)).contains("public @interface ForOpenAi");

        // Verify generated code compiles and the bearer token is optional at bootstrap like every other credential
        Path classesDir = verifyGeneratedCodeCompiles(outputPath);
        assertCredentialsOptional(classesDir, "io.trino.plugin.ai.generated.OpenAiClientConfig", "openai", "bearer-auth.token");
    }

    @Test
    void testOAuth2OnlyContractDoesNotRequireStaticApiKey(@TempDir Path outputPath)
            throws Exception
    {
        generate("oauth2-only.yaml", outputPath);

        Path configFile = outputPath.resolve("src/main/java/org/openapitools/client/ApiClientConfig.java");
        assertThat(Files.readString(configFile))
                .contains("@Config(\"api.service-oauth.token\")")
                .contains("setServiceOAuthToken")
                .doesNotContain("getApiKey", "@NotNull\n    public String getServiceOAuthToken()");
        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/StatusClient.java")))
                .contains("public StatusClient(HttpClient httpClient, URI baseUri, ApiCredentials credentials)")
                .doesNotContain("URI baseUri, String apiKey)");
        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/ApiOAuth2.java")))
                .contains("public static ClientCredentialsTokenProvider createServiceOAuthTokenProvider(");

        Path classesDir = verifyGeneratedCodeCompiles(outputPath);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> configClass = classLoader.loadClass("org.openapitools.client.ApiClientConfig");
            assertThat(configurationErrors(configClass, Map.of("api.base-uri", "https://example.test"))).isEmpty();
        }
    }

    @Test
    void testPublicOnlyOperationsDoNotRequireCredentials(@TempDir Path outputPath)
            throws Exception
    {
        generate("public-only.yaml", outputPath);

        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/ApiClientConfig.java")))
                .doesNotContain("getCredentials", "getApiKey", "serviceBearer");
        assertThat(Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/StatusClient.java")))
                .doesNotContain("authenticationAlternative", "getCredentials()");
        assertThat(outputPath.resolve("src/main/java/org/openapitools/client/ApiCredentials.java")).doesNotExist();

        Path classesDir = verifyGeneratedCodeCompiles(outputPath);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> configClass = classLoader.loadClass("org.openapitools.client.ApiClientConfig");
            assertThat(configurationErrors(configClass, Map.of("api.base-uri", "https://example.test"))).isEmpty();
        }
    }

    @Test
    void testOptionalAuthenticationTriesCredentialsFirst(@TempDir Path outputPath)
            throws Exception
    {
        generate("optional-authentication.yaml", outputPath);

        String client = Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/StatusClient.java"));
        assertThat(client).contains("if (credentials.hasServiceBearer()) {");
        assertThat(client).contains("else if (true) {");
        assertThat(client.indexOf("if (credentials.hasServiceBearer()) {")).isLessThan(client.indexOf("else if (true) {"));
        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testRejectsDigitLeadingAuthenticationName(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("digit-authentication-name.yaml", outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Security scheme name '1key' does not generate a valid Java identifier");
    }

    @Test
    void testRejectsCollidingAuthenticationHeaders(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("colliding-authentication-headers.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Security requirement for operation 'status' applies several schemes to the authorization header");
    }

    @Test
    void testRejectsHeaderParameterSetBySecurityRequirement(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("security-header-parameter.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Operation 'status' header parameter 'Authorization' is also set by its security requirement");
    }

    @Test
    void testGeneratesHeaderApiKeyConfiguration(@TempDir Path outputPath)
            throws Exception
    {
        generate("header-api-key.yaml", outputPath);

        Path configFile = outputPath.resolve("src/main/java/org/openapitools/client/ApiClientConfig.java");
        assertThat(Files.readString(configFile))
                .contains("@Config(\"api.service-key.api-key\")")
                .contains("builder.withServiceKey(serviceKeyApiKey)")
                .doesNotContain("@Config(\"api.api-key\")", "getApiKey()", "unusedBasic", "Username", "Password");

        Path classesDir = verifyGeneratedCodeCompiles(outputPath);
        assertCredentialsOptional(classesDir, "org.openapitools.client.ApiClientConfig", "api", "service-key.api-key");
    }

    @Test
    void testClientNameOverride(@TempDir Path outputPath)
            throws Exception
    {
        String inputSpec = getClass().getClassLoader().getResource("openai-chat.yaml").getFile();

        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("airlift-http-client")
                .setInputSpec(inputSpec)
                .setOutputDir(outputPath.toString())
                .addAdditionalProperty("projectName", "openai")
                .addAdditionalProperty("clientName", "OpenAi")
                .addAdditionalProperty("apiPackage", "com.example.api")
                .addAdditionalProperty("modelPackage", "com.example.model")
                .addAdditionalProperty("invokerPackage", "com.example");

        ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(clientOptInput).generate();

        // Verify clientName override produces correct PascalCase
        Path annotation = outputPath.resolve("src/main/java/com/example/ForOpenAi.java");
        assertThat(annotation).exists();
        assertThat(Files.readString(annotation)).contains("public @interface ForOpenAi");

        Path config = outputPath.resolve("src/main/java/com/example/OpenAiClientConfig.java");
        assertThat(config).exists();

        Path module = outputPath.resolve("src/main/java/com/example/OpenAiClientModule.java");
        assertThat(module).exists();
    }

    @Test
    void testGeneratedPomVersionOverrides(@TempDir Path outputPath)
            throws Exception
    {
        String inputSpec = getClass().getClassLoader().getResource("petstore.yaml").getFile();

        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("airlift-http-client")
                .setInputSpec(inputSpec)
                .setOutputDir(outputPath.toString())
                .addAdditionalProperty("projectName", "petstore")
                .addAdditionalProperty("groupId", "com.example")
                .addAdditionalProperty("artifactId", "petstore-client")
                .addAdditionalProperty("artifactVersion", "1.0.0")
                .addAdditionalProperty("javaVersion", "21")
                .addAdditionalProperty("airbaseVersion", "999")
                .addAdditionalProperty("airliftVersion", "1.2.3");

        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        String pomContent = Files.readString(outputPath.resolve("pom.xml"));
        assertThat(pomContent).contains("<version>999</version>");
        assertThat(pomContent).contains("<project.build.targetJdk>21</project.build.targetJdk>");
        assertThat(pomContent).contains("<dep.airlift.version>1.2.3</dep.airlift.version>");
    }

    @Test
    void testGeneratesArrayQueryParameterSerialization(@TempDir Path outputPath)
            throws Exception
    {
        generate("array-parameters.yaml", outputPath);

        String client = Files.readString(outputPath.resolve("src/main/java/org/openapitools/client/api/ItemsClient.java"));
        // form style explodes by default, so filter and tags repeat the parameter while ids is comma-joined
        assertThat(client).contains("filter.forEach(value -> uriBuilder.addParameter(\"filter\", String.valueOf(value)));");
        assertThat(client).contains("tags.forEach(value -> uriBuilder.addParameter(\"tags\", String.valueOf(value)));");
        assertThat(client).contains("uriBuilder.addParameter(\"ids\", ids.stream().map(String::valueOf).collect(joining(\",\")));");
        assertThat(client).doesNotContain("String.valueOf(filter)", "String.valueOf(ids)", "String.valueOf(tags)");
        // an array header parameter is part of the signature, so the List import must be present even without a list codec
        assertThat(client).contains("List<String> xTrace").contains("import java.util.List;");
        verifyGeneratedCodeCompiles(outputPath);
    }

    @Test
    void testRejectsUnsupportedArrayParameterStyle(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("unsupported-array-style.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Query parameter 'ids' in operation 'listItems' uses unsupported array style 'pipeDelimited'");
    }

    @Test
    void testRejectsArrayPathParameters(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("path-array-parameter.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Path parameter 'ids' in operation 'getItems' must not be an array");
    }

    @Test
    void testRejectsCookieParameters(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("cookie-parameter.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Operation 'status' uses unsupported cookie parameters");
    }

    @Test
    void testRejectsUnsupportedHttpMethod(@TempDir Path outputPath)
    {
        assertThatThrownBy(() -> generate("unsupported-method.yaml", outputPath))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Operation 'statusHead' uses unsupported HTTP method HEAD");
    }

    private static void generate(String resourceName, Path outputPath)
    {
        String inputSpec = AirliftHttpClientCodegenIntegrationTest.class.getClassLoader().getResource(resourceName).getFile();
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("airlift-http-client")
                .setInputSpec(inputSpec)
                .setOutputDir(outputPath.toString());
        new DefaultGenerator()
                .opts(configurator.toClientOptInput())
                .generate();
    }

    private List<File> collectJavaFiles(Path sourceDir)
            throws IOException
    {
        if (!Files.exists(sourceDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .toList();
        }
    }

    private Path verifyGeneratedCodeCompiles(Path outputDir)
            throws IOException
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler)
                .describedAs("Java compiler not available - ensure running on JDK, not JRE")
                .isNotNull();

        Path sourceDir = outputDir.resolve("src/main/java");
        List<File> javaFiles = collectJavaFiles(sourceDir);
        assertThat(javaFiles)
                .describedAs("Should have generated Java files to compile")
                .isNotEmpty();

        Path classesDir = outputDir.resolve("target/classes");
        Files.createDirectories(classesDir);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

            String classpath = System.getProperty("java.class.path");

            List<String> options = List.of(
                    "-proc:none",
                    "-implicit:none",
                    "-d",
                    classesDir.toString(),
                    "-classpath",
                    classpath);

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits);

            boolean success = task.call();

            if (!success) {
                String errors = diagnostics.getDiagnostics().stream()
                        .map(diagnostic -> "  %s: %s at line %d in %s".formatted(
                                diagnostic.getKind(),
                                diagnostic.getMessage(null),
                                diagnostic.getLineNumber(),
                                diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown"))
                        .collect(joining("\n"));
                fail("Generated code failed to compile:\n%s".formatted(errors));
            }
        }
        return classesDir;
    }
}
