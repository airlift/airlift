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
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions("3.9.12")
public class OpenApiGenerationTest
{
    @Rule
    public final TestResources resources = new TestResources();

    public final MavenRuntime maven;

    public OpenApiGenerationTest(MavenRuntimeBuilder mavenBuilder)
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

    @Test
    public void testGenerateOpenapiFromServiceClasses()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        assertThat(openapiFile)
                .describedAs("OpenAPI spec should be generated at target/openapi.json")
                .exists()
                .isFile();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode openapi = mapper.readTree(openapiFile);

        assertThat(openapi.has("openapi"))
                .describedAs("OpenAPI spec should have 'openapi' field")
                .isTrue();
        assertThat(openapi.get("openapi").asText())
                .describedAs("OpenAPI version should be 3.0.x")
                .startsWith("3.0");

        assertThat(openapi.has("info"))
                .describedAs("OpenAPI spec should have 'info' section")
                .isTrue();
        assertThat(openapi.get("info").get("title").asText())
                .describedAs("API title should be 'Test API'")
                .isEqualTo("Test API");

        assertThat(openapi.has("paths"))
                .describedAs("OpenAPI spec should have 'paths' section")
                .isTrue();

        assertThat(openapi.has("components"))
                .describedAs("OpenAPI spec should have 'components' section")
                .isTrue();
        assertThat(openapi.get("components").has("schemas"))
                .describedAs("OpenAPI spec should have 'components/schemas' section")
                .isTrue();
    }

    @Test
    public void testWidgetServiceEndpoints()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode openapi = mapper.readTree(openapiFile);
        JsonNode paths = openapi.get("paths");

        boolean hasWidgetListEndpoint = false;
        for (var entry : paths.properties()) {
            String path = entry.getKey();
            if (path.contains("widget") && !path.contains("{")) {
                hasWidgetListEndpoint = true;
                JsonNode pathItem = entry.getValue();
                assertThat(pathItem.has("get"))
                        .describedAs("Widget list path should have GET method")
                        .isTrue();
                break;
            }
        }
        assertThat(hasWidgetListEndpoint)
                .describedAs("Should have a widget list endpoint (without path params)")
                .isTrue();

        boolean hasWidgetIdEndpoint = false;
        for (var entry : paths.properties()) {
            String path = entry.getKey();
            if (path.contains("widget") && path.contains("{")) {
                hasWidgetIdEndpoint = true;
                JsonNode pathItem = entry.getValue();
                assertThat(pathItem.has("get"))
                        .describedAs("Widget ID path should have GET method for getWidget")
                        .isTrue();
                assertThat(pathItem.has("delete"))
                        .describedAs("Widget ID path should have DELETE method for deleteWidget")
                        .isTrue();
                break;
            }
        }
        assertThat(hasWidgetIdEndpoint)
                .describedAs("Should have a widget ID endpoint with path parameter")
                .isTrue();
    }

    @Test
    public void testWidgetSchemas()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode openapi = mapper.readTree(openapiFile);
        JsonNode schemas = openapi.get("components").get("schemas");

        assertThat(hasSchemaLike(schemas, "widget"))
                .describedAs("Should have Widget schema")
                .isTrue();

        assertThat(hasSchemaLike(schemas, "newwidget"))
                .describedAs("Should have NewWidget schema")
                .isTrue();
    }

    @Test
    public void testItemServicePolymorphicEndpoints()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode openapi = mapper.readTree(openapiFile);
        JsonNode paths = openapi.get("paths");

        boolean hasItemsEndpoint = false;
        for (var entry : paths.properties()) {
            String path = entry.getKey();
            if (path.contains("item")) {
                hasItemsEndpoint = true;
                break;
            }
        }
        assertThat(hasItemsEndpoint)
                .describedAs("Should have items endpoint for ItemService")
                .isTrue();
    }

    @Test
    public void testPolymorphicSchemas()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode openapi = mapper.readTree(openapiFile);
        JsonNode schemas = openapi.get("components").get("schemas");

        boolean hasBookSchema = hasSchemaLike(schemas, "book");
        boolean hasMovieSchema = hasSchemaLike(schemas, "movie");

        assertThat(hasBookSchema || hasMovieSchema)
                .describedAs("Should have at least one polymorphic subtype schema (Book or Movie)")
                .isTrue();
    }

    @Test
    public void testPrettyPrintedOutput()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "target/openapi.json");
        String content = Files.readString(openapiFile.toPath());

        assertThat(content)
                .describedAs("OpenAPI JSON should be pretty-printed with indentation")
                .contains("\n")
                .containsPattern("\"\\w+\"\\s*:");
    }

    @Test
    public void testGenerateOpenapiFromScanPackages()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .withCliOptions("-Dapi.scanPackages=io.airlift.test")
                .execute("compile", "process-classes")
                .assertErrorFreeLog()
                .assertLogText("Scanning packages");

        File openapiFile = new File(basedir, "target/openapi.json");
        assertThat(openapiFile)
                .describedAs("OpenAPI spec should be generated when using package scan")
                .exists()
                .isFile();
    }

    @Test
    public void testRelativeOutputFilePath()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");

        maven.forProject(basedir)
                .withCliOptions("-Dapi.outputFile=openapi.json")
                .execute("compile", "process-classes")
                .assertErrorFreeLog();

        File openapiFile = new File(basedir, "openapi.json");
        assertThat(openapiFile)
                .describedAs("OpenAPI spec should support output path without a parent directory")
                .exists()
                .isFile();
    }

    @Test
    public void testInvalidSecuritySchemeFailsBuild()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");
        File openapiFile = new File(basedir, "target/openapi.json");

        maven.forProject(basedir)
                .withCliOptions("-Dapi.securityScheme=NOT_A_SCHEME")
                .execute("clean", "compile", "process-classes")
                .assertLogText("Invalid security scheme")
                .assertNoLogText("BUILD SUCCESS");

        assertThat(openapiFile)
                .describedAs("OpenAPI spec should not be written when security scheme is invalid")
                .doesNotExist();
    }

    @Test
    public void testInvalidServiceTypeClassFailsBuild()
            throws Exception
    {
        File basedir = resources.getBasedir("full-service");
        File openapiFile = new File(basedir, "target/openapi.json");

        maven.forProject(basedir)
                .withCliOptions("-Dapi.serviceTypeClass=java.lang.String")
                .execute("clean", "compile", "process-classes")
                .assertLogText("must implement ApiServiceType")
                .assertNoLogText("BUILD SUCCESS");

        assertThat(openapiFile)
                .describedAs("OpenAPI spec should not be written when service type class is invalid")
                .doesNotExist();
    }

    private boolean hasSchemaLike(JsonNode schemas, String namePart)
    {
        String normalizedNamePart = namePart.toLowerCase(Locale.ROOT);
        for (var entry : schemas.properties()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(normalizedNamePart)) {
                return true;
            }
        }
        return false;
    }
}
