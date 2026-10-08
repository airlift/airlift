package io.airlift.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodecFactory;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.model.GetSkillRequest;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ReadResourceDirectoryRequest;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.McpSkillBuilder.mcpSkillBuilder;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.handler.ResourceKind.DYNAMIC_SKILL;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_DIRECTORY_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_SERVER_DISCOVER;
import static io.airlift.mcp.model.Constants.METHOD_SKILLS_GET;
import static io.airlift.mcp.model.Constants.METHOD_SKILLS_LIST;
import static io.airlift.mcp.model.Constants.SKILLS_EXTENSION;
import static io.airlift.mcp.model.Constants.SKILL_MIME_TYPE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcpSkills
{
    private static final String SKILL_URI = "skill://a/b/c/my-test-skill/SKILL.md";
    private static final String GUIDE_URI = "skill://a/b/c/my-test-skill/references/GUIDE.md";
    private static final String DYNAMIC_SKILL_URI = "skill://my-dynamic-skill/SKILL.md";
    private static final String TEMPLATE_SKILL_URI = "skill://a/xyz/my-test-skill-template/SKILL.md";

    private TestingServer testingServer;
    private JsonMapper jsonMapper;
    private JsonCodecFactory jsonCodecFactory;
    private URI uri;

    @BeforeAll
    public void setup()
    {
        testingServer = new TestingServer(ImmutableMap.of(), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .addIcon("google", binding -> binding.toInstance(new Icon("https://example.com/favicon.ico")))
                .withAllInClass(TestingEndpoints.class)
                .build());
        jsonMapper = testingServer.injector().getInstance(JsonMapper.class);
        jsonCodecFactory = new JsonCodecFactory(jsonMapper);
        uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
    }

    @AfterAll
    public void shutdown()
    {
        testingServer.close();
    }

    @Test
    public void testExtensionDeclared()
    {
        Map<String, Object> capabilities = map(result(call(METHOD_SERVER_DISCOVER, ImmutableMap.of())).get("capabilities"));

        assertThat(capabilities).containsKey("resources");
        // the skill template's instances can't be enumerated, so directoryRead is not declared
        assertThat(map(capabilities.get("extensions")))
                .containsEntry(SKILLS_EXTENSION, ImmutableMap.of());
    }

    @Test
    public void testDirectoryReadDeclaredWithoutSkillNamespaceTemplates()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);
        ResourceTemplateEntry skillTemplate = testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<ResourceTemplateEntry>>() {}))
                .stream()
                .filter(ResourceTemplateEntry::isSkill)
                .collect(onlyElement());

        entities.removeResourceTemplate(skillTemplate.resourceTemplate().uriTemplate());
        try {
            Map<String, Object> capabilities = map(result(call(METHOD_SERVER_DISCOVER, ImmutableMap.of())).get("capabilities"));
            assertThat(map(capabilities.get("extensions")))
                    .containsEntry(SKILLS_EXTENSION, ImmutableMap.of("directoryRead", true));
        }
        finally {
            entities.addSkillTemplate(skillTemplate.resourceTemplate(), skillTemplate.handler(), skillTemplate.kind() == DYNAMIC_SKILL);
        }
    }

    @Test
    public void testSupportingFileWithoutSingleContentMakesSkillDynamic()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String twoContentsUri = "skill://a/b/c/my-test-skill/references/two.md";
        Resource twoContents = new Resource("two", twoContentsUri, Optional.empty(), "text/markdown", OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addResource(twoContents, (_, resource, _) -> new ReadResourceResult(ImmutableList.of(
                new ResourceContents(resource.name(), resource.uri(), resource.mimeType(), "one"),
                new ResourceContents(resource.name(), resource.uri(), resource.mimeType(), "two"))));
        try {
            assertThat(getSkill(SKILL_URI)).containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResource(twoContentsUri);
        }

        String noContentsUri = "skill://a/b/c/my-test-skill/references/none.md";
        Resource noContents = new Resource("none", noContentsUri, Optional.empty(), "text/markdown", OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addResource(noContents, (_, _, _) -> new ReadResourceResult(ImmutableList.of()));
        try {
            assertThat(getSkill(SKILL_URI)).containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResource(noContentsUri);
        }

        assertThat(getSkill(SKILL_URI)).containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));
    }

    @Test
    public void testInvalidSkillIsOmittedFromListing()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String brokenUri = "skill://broken/broken-skill/SKILL.md";
        Resource broken = new Resource("broken-skill", brokenUri, Optional.of("A skill whose content has the wrong name."), SKILL_MIME_TYPE, OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addSkill(broken, (_, resource, _) -> new ReadResourceResult(ImmutableList.of(new ResourceContents(resource.name(), resource.uri(), resource.mimeType(), "---\nname: other-name\ndescription: Wrong name\n---\n"))), false);
        try {
            assertThat(list(result(call(METHOD_SKILLS_LIST, ImmutableMap.of())).get("skills")))
                    .extracting(skill -> skill.get("uri"))
                    .containsExactly(SKILL_URI, DYNAMIC_SKILL_URI);

            Map<String, Object> response = call(METHOD_SKILLS_GET, new GetSkillRequest(brokenUri));
            assertThat(response).doesNotContainKey("result");
            assertThat(map(response.get("error"))).containsEntry("code", INTERNAL_ERROR.code());
        }
        finally {
            entities.removeResource(brokenUri);
        }
    }

    @Test
    public void testSkillTemplateWithExplicitRegexIsDynamic()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        // {path} can span segments, so the template also serves files below the root of each skill it serves
        String templateUri = "skill://regex/{path: .+}/regex-skill/SKILL.md";
        ResourceTemplate template = new ResourceTemplate("regex-skill", templateUri, Optional.of("A skill template with a multi-segment variable."), SKILL_MIME_TYPE, Optional.empty(), Optional.empty(), Optional.empty());
        entities.addSkillTemplate(template, (_, resourceTemplate, request, _) -> skillContents(new Resource(resourceTemplate.name(), request.uri(), resourceTemplate.description(), resourceTemplate.mimeType(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty()), request.uri()), false);
        try {
            assertThat(getSkill("skill://regex/x/regex-skill/SKILL.md")).containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResourceTemplate(templateUri);
        }
    }

    @Test
    public void testListSkills()
    {
        Map<String, Object> result = result(call(METHOD_SKILLS_LIST, ImmutableMap.of()));

        assertThat(result)
                .containsEntry("resultType", "complete")
                .containsKeys("ttlMs", "cacheScope")
                .doesNotContainKey("nextCursor");

        List<Map<String, Object>> skills = list(result.get("skills"));
        assertThat(skills)
                .extracting(skill -> skill.get("uri"))
                .containsExactly(SKILL_URI, DYNAMIC_SKILL_URI);

        assertThat(skills.getFirst())
                .containsEntry("frontmatter", ImmutableMap.of("name", "my-test-skill", "description", "An example skill."))
                .containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));

        assertThat(skills.getLast())
                .containsEntry("frontmatter", ImmutableMap.of("name", "my-dynamic-skill", "description", "An example dynamic skill."))
                .containsEntry("resources", "dynamic");
    }

    @Test
    public void testGetSkill()
    {
        Map<String, Object> result = result(call(METHOD_SKILLS_GET, new GetSkillRequest(SKILL_URI)));

        assertThat(result)
                .containsEntry("resultType", "complete")
                .containsKeys("ttlMs", "cacheScope");
        assertThat(map(result.get("skill")))
                .containsEntry("uri", SKILL_URI)
                .containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));
    }

    @Test
    public void testGetSkillWithDifferentHostCase()
    {
        assertThat(getSkill("skill://A/b/c/my-test-skill/SKILL.md"))
                .containsEntry("uri", SKILL_URI)
                .containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));
    }

    @Test
    public void testGetUnlistedTemplateSkill()
    {
        Map<String, Object> skill = map(result(call(METHOD_SKILLS_GET, new GetSkillRequest(TEMPLATE_SKILL_URI))).get("skill"));

        assertThat(skill)
                .containsEntry("uri", TEMPLATE_SKILL_URI)
                .containsEntry("frontmatter", ImmutableMap.of("name", "my-test-skill-template", "description", "An example skill template."))
                .containsEntry("resources", List.of(expectedSkillResource(TEMPLATE_SKILL_URI)));
    }

    @Test
    public void testGetUnknownSkill()
    {
        assertInvalidParams(call(METHOD_SKILLS_GET, new GetSkillRequest("skill://acme/billing/chargebacks/SKILL.md")));
        assertInvalidParams(call(METHOD_SKILLS_GET, new GetSkillRequest(GUIDE_URI)));
        assertInvalidParams(call(METHOD_SKILLS_GET, new GetSkillRequest("file://example1.txt")));
        assertInvalidParams(call(METHOD_SKILLS_GET, new GetSkillRequest("not a uri")));
    }

    @Test
    public void testAddSkillProgrammatically()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String skillUri = "skill://added/added-skill/SKILL.md";
        Resource skill = new Resource("added-skill", skillUri, Optional.of("An added skill."), SKILL_MIME_TYPE, OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addSkill(skill, (_, resource, _) -> skillContents(resource, resource.uri()), false);

        String templateUri = "skill://added-templates/{version}/added-template/SKILL.md";
        ResourceTemplate skillTemplate = new ResourceTemplate("added-template", templateUri, Optional.of("An added skill template."), SKILL_MIME_TYPE, Optional.empty(), Optional.empty(), Optional.empty());
        entities.addSkillTemplate(skillTemplate, (_, resourceTemplate, request, _) -> skillContents(new Resource(resourceTemplate.name(), request.uri(), resourceTemplate.description(), resourceTemplate.mimeType(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty()), request.uri()), true);

        try {
            assertThat(list(result(call(METHOD_SKILLS_LIST, ImmutableMap.of())).get("skills")))
                    .extracting(entry -> entry.get("uri"))
                    .contains(skillUri);

            assertThat(map(result(call(METHOD_SKILLS_GET, new GetSkillRequest(skillUri))).get("skill")))
                    .containsEntry("frontmatter", ImmutableMap.of("name", "added-skill", "description", "An added skill."))
                    .containsEntry("resources", List.of(expectedSkillResource(skillUri)));

            assertThat(map(result(call(METHOD_SKILLS_GET, new GetSkillRequest("skill://added-templates/v2/added-template/SKILL.md"))).get("skill")))
                    .containsEntry("frontmatter", ImmutableMap.of("name", "added-template", "description", "An added skill template."))
                    .containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResource(skillUri);
            entities.removeResourceTemplate(templateUri);
        }

        assertInvalidParams(call(METHOD_SKILLS_GET, new GetSkillRequest(skillUri)));
    }

    @Test
    public void testAddInvalidSkill()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        Resource wrongName = new Resource("added-skill", "skill://added/other-name/SKILL.md", Optional.of("An added skill."), SKILL_MIME_TYPE, OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> entities.addSkill(wrongName, (_, resource, _) -> skillContents(resource, resource.uri()), false))
                .isInstanceOf(IllegalArgumentException.class);

        Resource wrongMimeType = new Resource("added-skill", "skill://added/added-skill/SKILL.md", Optional.of("An added skill."), "text/plain", OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> entities.addSkill(wrongMimeType, (_, resource, _) -> skillContents(resource, resource.uri()), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ReadResourceResult skillContents(Resource resource, String uri)
    {
        String content = mcpSkillBuilder(resource).addContent("Added").buildSkill();
        return new ReadResourceResult(ImmutableList.of(new ResourceContents(resource.name(), uri, SKILL_MIME_TYPE, content)));
    }

    @Test
    public void testTemplateBelowSkillMakesItDynamic()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String templateUri = "skill://a/b/c/my-test-skill/templates/{id}.md";
        ResourceTemplate template = new ResourceTemplate("my-test-skill-templates", templateUri, Optional.empty(), "text/markdown", Optional.empty(), Optional.empty(), Optional.empty());
        entities.addResourceTemplate(template, (_, _, request, _) -> new ReadResourceResult(ImmutableList.of(new ResourceContents("template", request.uri(), "text/markdown", "# Template"))));
        try {
            assertThat(getSkill(SKILL_URI)).containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResourceTemplate(templateUri);
        }

        assertThat(getSkill(SKILL_URI)).containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));
    }

    @Test
    public void testNestedDynamicSkillMakesEnclosingSkillDynamic()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String nestedUri = "skill://a/b/c/my-test-skill/nested-skill/SKILL.md";
        Resource nested = new Resource("nested-skill", nestedUri, Optional.of("A nested dynamic skill."), SKILL_MIME_TYPE, OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addSkill(nested, (_, resource, _) -> skillContents(resource, resource.uri()), true);
        try {
            assertThat(getSkill(SKILL_URI)).containsEntry("resources", "dynamic");
            assertThat(getSkill(nestedUri)).containsEntry("resources", "dynamic");
        }
        finally {
            entities.removeResource(nestedUri);
        }
    }

    @Test
    public void testExplicitDirectoryResource()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String emptyUri = "skill://a/b/c/my-test-skill/empty";
        Resource empty = new Resource("empty", emptyUri, Optional.empty(), "inode/directory", OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addResource(empty, (_, _, _) -> {
            throw new AssertionError("directory resources are not read");
        });
        try {
            assertThat(readDirectory(emptyUri)).isEmpty();
            assertThat(readDirectory("skill://a/b/c/my-test-skill"))
                    .extracting(resource -> resource.get("uri"))
                    .containsExactly(SKILL_URI, emptyUri, "skill://a/b/c/my-test-skill/references");
            assertThat(getSkill(SKILL_URI)).containsEntry("resources", List.of(expectedSkillResource(SKILL_URI), expectedSkillResource(GUIDE_URI)));
        }
        finally {
            entities.removeResource(emptyUri);
        }
    }

    @Test
    public void testFileWithDescendantsIsNotDirectory()
    {
        McpEntities entities = testingServer.injector().getInstance(McpEntities.class);

        String fileUri = "skill://a/b/c/my-test-skill/references";
        Resource file = new Resource("references-file", fileUri, Optional.empty(), "text/plain", OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        entities.addResource(file, (_, resource, _) -> new ReadResourceResult(ImmutableList.of(new ResourceContents(resource.name(), resource.uri(), resource.mimeType(), "not a directory"))));
        try {
            assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest(fileUri)));
        }
        finally {
            entities.removeResource(fileUri);
        }
    }

    private Map<String, Object> getSkill(String skillUri)
    {
        return map(result(call(METHOD_SKILLS_GET, new GetSkillRequest(skillUri))).get("skill"));
    }

    @Test
    public void testReadDirectory()
    {
        assertThat(readDirectory("skill://a/b/c/my-test-skill"))
                .containsExactly(
                        ImmutableMap.of("uri", SKILL_URI, "name", "my-test-skill", "description", "An example skill.", "mimeType", "text/markdown"),
                        ImmutableMap.of("uri", "skill://a/b/c/my-test-skill/references", "name", "references", "mimeType", "inode/directory"));

        assertThat(readDirectory("skill://a/b/c/my-test-skill/references"))
                .containsExactly(ImmutableMap.of("uri", GUIDE_URI, "name", "my-test-skill-guide", "description", "Supporting file for my-test-skill", "mimeType", "text/markdown"));

        assertThat(readDirectory("skill://a"))
                .containsExactly(ImmutableMap.of("uri", "skill://a/b", "name", "b", "mimeType", "inode/directory"));

        assertThat(readDirectory("skill://my-dynamic-skill"))
                .extracting(resource -> resource.get("uri"))
                .containsExactly(DYNAMIC_SKILL_URI);

        assertThat(readDirectory("skill://a/xyz/my-test-skill-template"))
                .containsExactly(ImmutableMap.of("uri", TEMPLATE_SKILL_URI, "name", "my-test-skill-template", "description", "An example skill template.", "mimeType", "text/markdown"));
    }

    @Test
    public void testReadNonDirectory()
    {
        assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest(SKILL_URI)));
        assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest("skill://a/b/c/my-test-skill/")));
        assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest("skill://a/b/c/missing")));
        assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest("skill://")));
        assertInvalidParams(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest("file://example1.txt")));
    }

    private List<Map<String, Object>> readDirectory(String directoryUri)
    {
        Map<String, Object> result = result(call(METHOD_RESOURCES_DIRECTORY_READ, new ReadResourceDirectoryRequest(directoryUri)));
        assertThat(result)
                .containsEntry("resultType", "complete")
                .doesNotContainKey("nextCursor");
        return list(result.get("resources"));
    }

    private Map<String, Object> expectedSkillResource(String resourceUri)
    {
        List<Map<String, Object>> contents = list(result(call(METHOD_RESOURCES_READ, new ReadResourceRequest(resourceUri))).get("contents"));
        assertThat(contents).hasSize(1);

        byte[] bytes = ((String) contents.getFirst().get("text")).getBytes(UTF_8);
        return ImmutableMap.of(
                "uri", resourceUri,
                "digest", "sha256:" + Hashing.sha256().hashBytes(bytes),
                "size", bytes.length);
    }

    private static void assertInvalidParams(Map<String, Object> response)
    {
        assertThat(response).doesNotContainKey("result");
        assertThat(map(response.get("error"))).containsEntry("code", INVALID_PARAMS.code());
    }

    private Map<String, Object> call(String method, Object params)
    {
        Map<String, Object> paramsWithMeta = ImmutableMap.<String, Object>builder()
                .putAll(jsonMapper.convertValue(params, new TypeReference<Map<String, Object>>() {}))
                .put("_meta", ImmutableMap.of(
                        METADATA_PROTOCOL_VERSION, PROTOCOL_MCP_2026_07_28.value(),
                        METADATA_CLIENT_CAPABILITIES, ImmutableMap.of()))
                .buildKeepingLast();

        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, method, paramsWithMeta);

        Request request = preparePost().setUri(uri)
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .addHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2026_07_28.value())
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        JsonResponse<Map<String, Object>> response = testingServer.httpClient()
                .execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.hasValue()).isTrue();
        return response.getValue();
    }

    private static Map<String, Object> result(Map<String, Object> response)
    {
        assertThat(response).doesNotContainKey("error");
        return map(response.get("result"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value)
    {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value)
    {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }
}
