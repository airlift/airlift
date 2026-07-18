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
package io.airlift.api.openapi;

import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.openapi.models.SecurityScheme;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.airlift.api.ApiOpenApiTrait.USE_ONE_OF_DISCRIMINATORS;
import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.openapi.OpenApiMetadata.SecurityScheme.BEARER_ACCESS_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

public class TestGeneratedClientCompatibilityOpenApi
{
    @Test
    public void testLegacySecuritySchemesDeclareHttpType()
    {
        assertThat(legacySecurityScheme(BEARER_ACCESS_TOKEN, "accessToken")).satisfies(scheme -> {
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(scheme.getScheme()).isEqualTo("bearer");
            assertThat(scheme.getBearerFormat()).isEqualTo("Access token");
            assertThat(scheme.getName()).isNull();
            assertThat(scheme.getIn()).isNull();
        });
        assertThat(legacySecurityScheme(OpenApiMetadata.SecurityScheme.BEARER_JWT, "bearer")).satisfies(scheme -> {
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(scheme.getScheme()).isEqualTo("bearer");
            assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
            assertThat(scheme.getName()).isNull();
            assertThat(scheme.getIn()).isNull();
        });
        assertThat(legacySecurityScheme(OpenApiMetadata.SecurityScheme.BASIC, "Basic")).satisfies(scheme -> {
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(scheme.getScheme()).isEqualTo("basic");
            assertThat(scheme.getBearerFormat()).isNull();
            assertThat(scheme.getName()).isNull();
            assertThat(scheme.getIn()).isNull();
        });
    }

    private static SecurityScheme legacySecurityScheme(OpenApiMetadata.SecurityScheme legacyScheme, String expectedName)
    {
        ModelApi modelApi = apiBuilder().add(StatusService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(
                        modelApi.modelServices(),
                        new OpenApiMetadata(Optional.of(legacyScheme), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1),
                        ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);

        assertThat(openAPI.getSecurity()).hasSize(1);
        assertThat(openAPI.getSecurity().getFirst().getMap()).containsOnlyKeys(expectedName);
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsOnlyKeys(expectedName);
        return openAPI.getComponents().getSecuritySchemes().get(expectedName);
    }

    @Test
    public void testEnumComponentNameYieldsToCollidingSchemaNames()
    {
        ModelApi modelApi = apiBuilder().add(ModeService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(
                        modelApi.modelServices(),
                        new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1),
                        ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

        // the resource "Mode" and the enum named Mode, and the poly sub-schema "ModeChoiceFixed" and the enum named
        // ModeChoiceFixed, must all get distinct component names whichever is built first
        assertThat(schemas).containsKeys("Mode", "ModeChoice");
        Schema<?> mode = schemas.get("Mode");
        String selectionEnum = componentName(((Schema<?>) mode.getProperties().get("selection")).get$ref());
        assertThat(selectionEnum).isNotEqualTo("Mode");
        assertThat(schemas.get(selectionEnum).getEnum()).containsExactly("FAST", "SLOW");

        String fixedEnum = componentName(((Schema<?>) mode.getProperties().get("fixed")).get$ref());
        String fixedSubSchema = componentName(schemas.get("ModeChoice").getDiscriminator().getMapping().get("fixed"));
        assertThat(fixedEnum).isNotEqualTo(fixedSubSchema);
        assertThat(schemas.get(fixedEnum).getEnum()).containsExactly("YES", "NO");
        assertThat(schemas.get(fixedSubSchema).getAllOf()).isNotEmpty();

        Set<String> referenced = new HashSet<>();
        schemas.values().forEach(schema -> collectReferences(schema, referenced));
        assertThat(referenced).contains(selectionEnum, fixedEnum, fixedSubSchema);
        assertThat(schemas.keySet()).containsAll(referenced);
    }

    private static void collectReferences(Schema<?> schema, Set<String> referenced)
    {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            referenced.add(componentName(schema.get$ref()));
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> collectReferences((Schema<?>) property, referenced));
        }
        collectReferences(schema.getItems(), referenced);
        if (schema.getAdditionalProperties() instanceof Schema<?> additionalProperties) {
            collectReferences(additionalProperties, referenced);
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(item -> collectReferences((Schema<?>) item, referenced));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(item -> collectReferences((Schema<?>) item, referenced));
        }
        if ((schema.getDiscriminator() != null) && (schema.getDiscriminator().getMapping() != null)) {
            schema.getDiscriminator().getMapping().values().forEach(reference -> referenced.add(componentName(reference)));
        }
    }

    private static String componentName(String reference)
    {
        assertThat(reference).startsWith("#/components/schemas/");
        return reference.substring("#/components/schemas/".length());
    }

    @Test
    public void testSharedServiceTagAndEnumComponents()
    {
        ModelApi modelApi = apiBuilder()
                .add(FrameService.class)
                .add(StatusService.class)
                .build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(
                        modelApi.modelServices(),
                        new OpenApiMetadata(Optional.of(BEARER_ACCESS_TOKEN), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1),
                        ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);

        assertThat(openAPI.getTags())
                .filteredOn(tag -> tag.getName().equals("Compatibility Service"))
                .hasSize(1);
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("accessToken");

        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        assertThat(schemas).containsKey("FrameKind");
        assertThat(schemas.get("FrameKind").getEnum()).containsExactly("TEXT", "SQL");

        Schema<?> textFrame = schemas.values().stream()
                .filter(schema -> schema.getProperties() != null)
                .filter(schema -> schema.getProperties().containsKey("acceptedKinds"))
                .findFirst()
                .orElseThrow();
        // the described field keeps its description for 3.0 consumers by wrapping the shared reference in allOf
        Schema<?> kind = textFrame.getProperties().get("kind");
        assertThat(kind.getDescription()).isEqualTo("Kind");
        assertThat(kind.getAllOf()).hasSize(1);
        assertThat(((Schema<?>) kind.getAllOf().getFirst()).get$ref()).isEqualTo("#/components/schemas/FrameKind");
        assertThat(textFrame.getProperties().get("acceptedKinds").getItems().get$ref()).isEqualTo("#/components/schemas/FrameKind");
    }

    @Test
    public void testInlinePolymorphicSchemasDoNotBecomeComponents()
    {
        assertThat(nestedNodeOpenApi().getComponents().getSchemas())
                .containsKeys("NestedNode", "NestedNodeLeaf", "NestedNodeBranch")
                .doesNotContainKey("Leaf");
    }

    @Test
    public void testReferencedRecursiveInlineSchemasBecomeComponents()
    {
        Schema<?> branch = nestedNodeOpenApi().getComponents().getSchemas().get("Branch");

        assertThat(branch).isNotNull();
        assertThat(branch.getProperties().get("children").getItems().get$ref())
                .isEqualTo("#/components/schemas/Branch");
    }

    private static OpenAPI nestedNodeOpenApi()
    {
        ModelApi modelApi = apiBuilder()
                .add(NestedNodeService.class)
                .build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        return OpenApiProvider.create(
                        modelApi.modelServices(),
                        new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1),
                        ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);
    }

    public enum FrameKind
    {
        TEXT,
        SQL,
    }

    @ApiPolyResource(key = "frameType", name = "queryFrame", description = "Query frame", openApiTraits = USE_ONE_OF_DISCRIMINATORS)
    public sealed interface QueryFrame
    {
        @ApiResource(name = "textFrame", description = "TEXT frame")
        record TextFrame(
                @ApiDescription("Query") String query,
                @ApiDescription("Kind") FrameKind kind,
                @ApiDescription("Accepted kinds") List<FrameKind> acceptedKinds)
                implements QueryFrame {}

        @ApiResource(name = "sqlFrame", description = "SQL frame")
        record SqlFrame(
                @ApiDescription("Query") String query,
                @ApiDescription("Kind") FrameKind kind,
                @ApiDescription("Accepted kinds") List<FrameKind> acceptedKinds)
                implements QueryFrame {}
    }

    @ApiPolyResource(key = "nodeType", name = "nestedNode", description = "Nested node")
    public sealed interface NestedNode
    {
        @ApiResource(name = "leaf", description = "Leaf node")
        record Leaf(@ApiDescription("Value") String value)
                implements NestedNode {}

        @ApiResource(name = "branch", description = "Recursive branch")
        record Branch(@ApiDescription("Children") List<Branch> children)
                implements NestedNode {}
    }

    @ApiService(name = "nestedNode", type = CompatibilityServiceType.class, description = "Nested node operations")
    public static class NestedNodeService
    {
        @ApiGet(description = "Get a nested node")
        public NestedNode getNestedNode()
        {
            return new NestedNode.Leaf("value");
        }
    }

    @ApiResource(name = "serviceStatus", description = "Service status")
    public record ServiceStatus(FrameKind defaultKind) {}

    public static class CollidingEnums
    {
        public enum Mode
        {
            FAST,
            SLOW,
        }

        public enum ModeChoiceFixed
        {
            YES,
            NO,
        }
    }

    @ApiPolyResource(key = "choiceType", name = "modeChoice", description = "Mode choice")
    public sealed interface ModeChoice
    {
        @ApiResource(name = "fixed", description = "Fixed choice")
        record Fixed(String value)
                implements ModeChoice {}

        @ApiResource(name = "random", description = "Random choice")
        record Random(int seed)
                implements ModeChoice {}
    }

    @ApiResource(name = "mode", description = "Mode")
    public record ModeResource(CollidingEnums.Mode selection, ModeChoice choice, CollidingEnums.ModeChoiceFixed fixed) {}

    @ApiService(name = "mode", type = CompatibilityServiceType.class, description = "Mode operations")
    public static class ModeService
    {
        @ApiGet(description = "Get mode")
        public ModeResource getMode()
        {
            return new ModeResource(CollidingEnums.Mode.FAST, new ModeChoice.Fixed("x"), CollidingEnums.ModeChoiceFixed.YES);
        }
    }

    @ApiService(name = "compatibility", type = CompatibilityServiceType.class, description = "Compatibility operations")
    public static class FrameService
    {
        @ApiGet(description = "Get a query frame")
        public QueryFrame getFrame()
        {
            return new QueryFrame.TextFrame("query", FrameKind.TEXT, List.of(FrameKind.TEXT, FrameKind.SQL));
        }
    }

    @ApiService(name = "compatibility", type = CompatibilityServiceType.class, description = "Compatibility operations")
    public static class StatusService
    {
        @ApiGet(description = "Get service status")
        public ServiceStatus getStatus()
        {
            return new ServiceStatus(FrameKind.TEXT);
        }
    }

    public static class CompatibilityServiceType
            implements ApiServiceType
    {
        @Override
        public String id()
        {
            return "compatibility";
        }

        @Override
        public int version()
        {
            return 1;
        }

        @Override
        public String title()
        {
            return "Compatibility API";
        }

        @Override
        public String description()
        {
            return "Compatibility API";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return Set.of();
        }
    }
}
