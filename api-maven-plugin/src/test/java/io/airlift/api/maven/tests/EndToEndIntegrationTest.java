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
package io.airlift.api.maven.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.takari.maven.testing.TestResources5;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenPluginTest;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@MavenVersions({"3.9.11", "3.9.14"})
class EndToEndIntegrationTest
{
    @RegisterExtension
    final TestResources5 resources = new TestResources5();

    private final MavenRuntime maven;

    EndToEndIntegrationTest(MavenRuntimeBuilder mavenBuilder)
            throws Exception
    {
        String projectVersion = MavenTestSupport.resolveProjectVersion();
        this.maven = mavenBuilder
                .withCliOptions(
                        "-B",
                        "-U",
                        "-Dmaven.repo.local=" + MavenTestSupport.resolveLocalRepository(),
                        "-Dit-plugin.version=" + projectVersion,
                        "-Ddep.airlift.version=" + projectVersion)
                .build();
    }

    @MavenPluginTest
    void testBuildPipelineGeneratesOpenApi()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("clean", "compile", "process-classes")
                .assertErrorFreeLog();

        File openApiSpec = new File(basedir, "target/openapi.json");
        assertThat(openApiSpec)
                .describedAs("OpenAPI spec should be generated during process-classes")
                .exists()
                .isFile();

        JsonNode openapi = new ObjectMapper().readTree(openApiSpec);
        assertThat(openapi.has("openapi")).isTrue();
        assertThat(openapi.has("info")).isTrue();
        assertThat(openapi.has("paths")).isTrue();
        assertThat(openapi.has("components")).isTrue();
    }

    @MavenPluginTest
    void testGeneratedSpecIsValidOpenApi()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openApiSpec = new File(basedir, "target/openapi.json");
        SwaggerParseResult parseResult = new OpenAPIV3Parser()
                .readLocation(openApiSpec.toURI().toString(), null, null);

        assertThat(parseResult.getOpenAPI())
                .describedAs("Generated OpenAPI should parse successfully")
                .isNotNull();
        assertThat(parseResult.getMessages())
                .describedAs("Generated OpenAPI should not contain parser validation errors")
                .isNullOrEmpty();
    }

    @MavenPluginTest
    void testRepeatedGenerationIsDeterministic()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openApiSpec = new File(basedir, "target/openapi.json");
        String firstContent = Files.readString(openApiSpec.toPath());
        Files.deleteIfExists(openApiSpec.toPath());

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        String secondContent = Files.readString(openApiSpec.toPath());

        assertThat(secondContent)
                .describedAs("Repeated generation should produce deterministic OpenAPI JSON")
                .isEqualTo(firstContent);
    }

    @MavenPluginTest
    void testGeneratedSpecCanBeConsumedByStandardOpenApiGenerator()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("clean", "compile", "process-classes", "generate-test-sources")
                .assertErrorFreeLog();

        Path generatedClient = basedir.toPath()
                .resolve("target/openapi-test-client/io/airlift/test/generated/client/ApiClient.java");
        Path generatedApi = basedir.toPath()
                .resolve("target/openapi-test-client/io/airlift/test/generated/api");

        assertThat(generatedClient)
                .describedAs("OpenAPI Generator should produce a standard Java ApiClient from generated spec")
                .exists()
                .isRegularFile();
        assertThat(generatedApi)
                .describedAs("OpenAPI Generator should produce API stubs from generated spec")
                .exists()
                .isDirectory();
    }
}
