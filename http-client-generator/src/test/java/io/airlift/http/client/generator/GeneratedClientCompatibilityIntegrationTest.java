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

import io.takari.maven.testing.TestResources5;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenPluginTest;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.System.getProperty;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@MavenVersions("3.9.14")
class GeneratedClientCompatibilityIntegrationTest
{
    @RegisterExtension
    final TestResources5 resources = new TestResources5();

    private final MavenRuntime maven;

    GeneratedClientCompatibilityIntegrationTest(MavenRuntimeBuilder mavenBuilder)
            throws Exception
    {
        maven = mavenBuilder
                .withCliOptions(
                        "-B",
                        "-U",
                        "-Ddep.airlift.version=" + requireNonNull(getProperty("project.version"), "project.version system property is null"),
                        "-Ddep.openapi-generator.version=" + requireNonNull(getProperty("dep.openapi-generator.version"), "dep.openapi-generator.version system property is null"))
                .build();
    }

    @MavenPluginTest
    void testGeneratedClientCallsApiBuilderServer()
            throws Exception
    {
        File basedir = resources.getBasedir("generated-client-compatibility");

        maven.forProject(basedir)
                .execute("clean", "test")
                .assertErrorFreeLog();

        Path spec = basedir.toPath().resolve("target/openapi.json");
        assertThat(spec).exists();
        assertThat(Files.readString(spec))
                .contains("\"name\" : \"Compatibility Service\"")
                .containsOnlyOnce("\"name\" : \"Compatibility Service\"")
                .contains("\"type\" : \"http\"")
                .contains("\"scheme\" : \"bearer\"")
                .contains("\"discriminator\"")
                // a described enum property wraps its reference in allOf for a 3.0 contract so the description survives
                .containsSubsequence("\"defaultKind\"", "\"description\" : \"Default frame kind\"", "\"allOf\"", "\"$ref\" : \"#/components/schemas/FrameKind\"");

        Path generatedSources = basedir.toPath().resolve("target/generated-sources/openapi/io/airlift/openapi/client/generated");
        assertThat(Files.readString(generatedSources.resolve("api/CompatibilityServiceClient.java")))
                .contains("public CompatibilityServiceClient(HttpClient httpClient, URI baseUri, String apiKey)")
                .contains("public CompatibilityServiceClient(HttpClient httpClient, URI baseUri, BearerTokenProvider bearerTokenProvider)")
                .contains("import io.airlift.http.client.BearerTokenProvider;")
                .contains(".setHeader(AUTHORIZATION, \"Bearer \" + bearerToken)")
                .contains("retryPolicy.execute(\"getQueryFrame\", \"GET\", null, bearerTokenProvider")
                .contains("retryPolicy.execute(\"safeExecute\", \"POST\", idempotencyKey, bearerTokenProvider")
                .contains("requestBuilder.setHeader(\"Idempotency-Key\", String.valueOf(idempotencyKey))")
                .containsSubsequence(
                        "if (statusCode == 409)",
                        "return new ApiException(\"safeExecute\", exception, EXISTS_CODEC)")
                .containsSubsequence(
                        "if (statusCode == 409)",
                        "return new ApiException(\"readFailure\", exception, COMPATIBILITY_CONFLICT_CODEC)")
                .contains("createStatusResponseHandler(204)");
        assertThat(generatedSources.resolve("BearerTokenProvider.java")).doesNotExist();

        Path secondGeneratedSources = basedir.toPath().resolve("target/generated-sources/openapi-second/io/airlift/openapi/client/generated/second");
        assertThat(Files.readString(secondGeneratedSources.resolve("api/CompatibilityServiceClient.java")))
                .contains("import io.airlift.http.client.BearerTokenProvider;")
                .contains("public CompatibilityServiceClient(HttpClient httpClient, URI baseUri, BearerTokenProvider bearerTokenProvider)");
        assertThat(secondGeneratedSources.resolve("BearerTokenProvider.java")).doesNotExist();
        assertThat(Files.readString(generatedSources.resolve("model/QueryFrame.java")))
                .contains("@JsonTypeInfo")
                .contains("@JsonSubTypes.Type(value = TextFrame.class, name = \"textFrame\")")
                .contains("public sealed interface QueryFrame permits SqlFrame, TextFrame");
        assertThat(Files.readString(generatedSources.resolve("model/TextFrame.java")))
                .contains("implements QueryFrame")
                .contains("public enum FrameTypeEnum");
    }
}
