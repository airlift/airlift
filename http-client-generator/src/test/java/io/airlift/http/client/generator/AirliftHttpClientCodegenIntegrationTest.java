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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class AirliftHttpClientCodegenIntegrationTest
{
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
        assertThat(clientContent).contains("httpClient.execute(request, createJsonResponseHandler(");
        assertThat(clientContent).contains("createStatusResponseHandler()");

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
        assertThat(pomContent).doesNotContain("<artifactId>guava</artifactId>");

        // Verify generated code compiles
        verifyGeneratedCodeCompiles(outputPath);
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
        assertThat(clientContent).contains("private final String apiKey");

        // Bearer auth: Authorization header
        assertThat(clientContent).contains("import static io.airlift.http.client.HeaderNames.AUTHORIZATION;");
        assertThat(clientContent).contains(".setHeader(AUTHORIZATION, \"Bearer \" + apiKey)");

        // Constructor takes apiKey from config
        assertThat(clientContent).contains("config.getApiKey()");
        assertThat(clientContent).contains("requireNonNull(apiKey, \"apiKey is null\")");

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
        assertThat(clientContent).contains("httpClient.execute(request, createJsonResponseHandler(CHAT_RESPONSE_CODEC))");

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

        // Verify config has apiKey
        Path clientConfig = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/OpenAiClientConfig.java");
        assertThat(clientConfig).exists();
        String configContent = Files.readString(clientConfig);
        assertThat(configContent).contains("getApiKey");
        assertThat(configContent).contains("setApiKey");
        assertThat(configContent).contains("@Config(\"openai.api-key\")");
        assertThat(configContent).contains("@Config(\"openai.base-uri\")");

        // Verify binding annotation uses correct PascalCase
        Path annotation = outputPath.resolve(
                "src/main/java/io/trino/plugin/ai/generated/ForOpenAi.java");
        assertThat(annotation).exists();
        assertThat(Files.readString(annotation)).contains("public @interface ForOpenAi");

        // Verify generated code compiles
        verifyGeneratedCodeCompiles(outputPath);
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

    private void verifyGeneratedCodeCompiles(Path outputDir)
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
                    "-d", classesDir.toString(),
                    "-classpath", classpath);

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
    }
}
