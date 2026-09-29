package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpCapabilityFilter;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceKind;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Skill;
import io.airlift.mcp.model.SkillResource;
import io.airlift.mcp.model.SkillResources;
import io.airlift.mcp.model.Tool;
import org.glassfish.jersey.uri.UriTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpSkillBuilder.mcpSkillBuilder;
import static io.airlift.mcp.McpSkillBuilder.parseFrontmatter;
import static io.airlift.mcp.handler.ResourceKind.DYNAMIC_SKILL;
import static io.airlift.mcp.handler.ResourceKind.RESOURCE;
import static io.airlift.mcp.model.Constants.DIRECTORY_MIME_TYPE;
import static io.airlift.mcp.model.Constants.SKILL_MD_FILE;
import static io.airlift.mcp.model.Constants.SKILL_URI_PREFIX;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.reflection.SkillsHelper.skillKind;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;

public class InternalEntities
        implements McpEntities
{
    private static final Logger log = Logger.get(InternalEntities.class);

    // per-skill limits that every conforming host must accept
    private static final int MAX_SKILL_RESOURCES = 512;
    private static final long MAX_SKILL_SIZE = 16 * 1024 * 1024;

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final Map<String, PromptEntry> prompts = new ConcurrentHashMap<>();
    private final Map<URI, ResourceEntry> resources = new ConcurrentHashMap<>();
    private final Map<UriTemplate, ResourceTemplateEntry> resourceTemplates = new ConcurrentHashMap<>();
    private final Map<String, CompletionEntry> completions = new ConcurrentHashMap<>();
    private final Map<UriTemplate, Pattern> templatePatterns = new ConcurrentHashMap<>();
    private final McpCapabilityFilter capabilityFilter;

    @Inject
    InternalEntities(
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            Set<ResourceTemplateEntry> resourceTemplates,
            Set<CompletionEntry> completions,
            McpCapabilityFilter capabilityFilter)
    {
        this.capabilityFilter = requireNonNull(capabilityFilter, "capabilityFilter is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> putResource(resource.resource(), resource.handler(), resource.kind()));
        resourceTemplates.forEach(resourceTemplate -> putResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler(), resourceTemplate.kind()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));
    }

    @Override
    public void addTool(Tool tool, ToolHandler toolHandler)
    {
        tools.put(tool.name(), new ToolEntry(tool, toolHandler));
    }

    @Override
    public void removeTool(String toolName)
    {
        tools.remove(toolName);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        prompts.put(prompt.name(), new PromptEntry(prompt, promptHandler));
    }

    @Override
    public void removePrompt(String promptName)
    {
        prompts.remove(promptName);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        putResource(resource, handler, RESOURCE);
    }

    @Override
    public void addSkill(Resource skill, ResourceHandler handler, boolean dynamic)
    {
        putResource(skill, handler, skillKind(dynamic));
    }

    private void putResource(Resource resource, ResourceHandler handler, ResourceKind kind)
    {
        if (kind.isSkill()) {
            // this will validate required fields, etc.
            mcpSkillBuilder(resource).buildSkill();
        }
        resources.put(toUri(resource.uri()), new ResourceEntry(resource, handler, kind));
    }

    @Override
    public void removeResource(String resourceUri)
    {
        resources.remove(toUri(resourceUri));
    }

    @Override
    public void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
    {
        putResourceTemplate(resourceTemplate, handler, RESOURCE);
    }

    @Override
    public void addSkillTemplate(ResourceTemplate skillTemplate, ResourceTemplateHandler handler, boolean dynamic)
    {
        putResourceTemplate(skillTemplate, handler, skillKind(dynamic));
    }

    private void putResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler, ResourceKind kind)
    {
        if (kind.isSkill()) {
            // this will validate required fields, etc.
            mcpSkillBuilder(resourceTemplate).buildSkill();
        }
        UriTemplate key = toUriTemplate(resourceTemplate.uriTemplate());
        templatePatterns.put(key, Pattern.compile(key.getPattern().getRegex()));
        resourceTemplates.put(key, new ResourceTemplateEntry(resourceTemplate, handler, kind));
    }

    @Override
    public void removeResourceTemplate(String uriTemplate)
    {
        UriTemplate key = toUriTemplate(uriTemplate);
        resourceTemplates.remove(key);
        templatePatterns.remove(key);
    }

    @Override
    public void addCompletion(CompleteReference reference, CompletionHandler handler)
    {
        completions.put(completionKey(reference), new CompletionEntry(reference, handler));
    }

    @Override
    public void removeCompletion(CompleteReference reference)
    {
        completions.remove(completionKey(reference));
    }

    @Override
    public List<Tool> tools(McpRequestContext requestContext)
    {
        return tools.values()
                .stream()
                .map(ToolEntry::tool)
                .sorted(comparing(Tool::name))
                .filter(tool -> capabilityFilter.isAllowed(requestContext.identity(), tool))
                .collect(toImmutableList());
    }

    @Override
    public Optional<ToolEntry> toolEntry(McpRequestContext requestContext, String toolName)
    {
        return Optional.ofNullable(tools.get(toolName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.tool()));
    }

    @Override
    public void validateToolAllowed(McpRequestContext requestContext, String toolName)
    {
        Optional.ofNullable(tools.get(toolName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.tool()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Tool access not allowed: " + toolName));
                });
    }

    @Override
    public List<Prompt> prompts(McpRequestContext requestContext)
    {
        return prompts.values()
                .stream()
                .map(PromptEntry::prompt)
                .sorted(comparing(Prompt::name))
                .filter(prompt -> capabilityFilter.isAllowed(requestContext.identity(), prompt))
                .collect(toImmutableList());
    }

    @Override
    public Optional<PromptEntry> promptEntry(McpRequestContext requestContext, String promptName)
    {
        return Optional.ofNullable(prompts.get(promptName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.prompt()));
    }

    @Override
    public void validatePromptAllowed(McpRequestContext requestContext, String promptName)
    {
        Optional.ofNullable(prompts.get(promptName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.prompt()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Prompt access not allowed: " + promptName));
                });
    }

    @Override
    public List<Resource> resources(McpRequestContext requestContext)
    {
        return resources.values()
                .stream()
                .map(ResourceEntry::resource)
                .sorted(comparing(Resource::name))
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .collect(toImmutableList());
    }

    @Override
    public Optional<ResourceEntry> resourceEntry(McpRequestContext requestContext, String uri)
    {
        return Optional.ofNullable(resources.get(toUri(uri)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()));
    }

    @Override
    public void validateResourceAllowed(McpRequestContext requestContext, String uri)
    {
        Optional.ofNullable(resources.get(toUri(uri)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + uri));
                });
    }

    @Override
    public List<ResourceTemplate> resourceTemplates(McpRequestContext requestContext)
    {
        return resourceTemplates.values()
                .stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .sorted(comparing(ResourceTemplate::name))
                .filter(resourceTemplate -> capabilityFilter.isAllowed(requestContext.identity(), resourceTemplate))
                .collect(toImmutableList());
    }

    @Override
    public List<CompleteReference> completions(McpRequestContext requestContext)
    {
        return completions.values()
                .stream()
                .map(CompletionEntry::reference)
                .sorted(comparing(CompleteReference::sortValue))
                .filter(completeReference -> capabilityFilter.isAllowed(requestContext.identity(), completeReference))
                .collect(toImmutableList());
    }

    @Override
    public Optional<CompletionEntry> completionEntry(McpRequestContext requestContext, CompleteReference ref)
    {
        return Optional.ofNullable(completions.get(completionKey(ref)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.reference()));
    }

    @Override
    public Optional<ReadResourceResult> readResourceContents(McpRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        if (!capabilityFilter.isAllowed(requestContext.identity(), readResourceRequest.uri())) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
        }

        URI uri = URI.create(readResourceRequest.uri());
        ResourceEntry resourceEntry = resources.get(uri);
        if (resourceEntry != null) {
            if (!capabilityFilter.isAllowed(requestContext.identity(), resourceEntry.resource())) {
                throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
            }
        }

        for (Map.Entry<UriTemplate, ResourceTemplateEntry> entry : resourceTemplates.entrySet()) {
            if (entry.getKey().match(readResourceRequest.uri(), new HashMap<>())) {
                if (!capabilityFilter.isAllowed(requestContext.identity(), entry.getValue().resourceTemplate())) {
                    throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
                }
                break;
            }
        }

        return findResource(readResourceRequest.uri())
                .map(readResourceEntry -> readResourceEntry.handler().readResource(requestContext, readResourceEntry.resource(), readResourceRequest))
                .or(() -> findResourceTemplate(readResourceRequest.uri()).map(match -> match.entry.handler().readResourceTemplate(requestContext, match.entry.resourceTemplate(), readResourceRequest, match.values)));
    }

    @Override
    public boolean hasSkills(McpRequestContext requestContext)
    {
        return resources.values()
                .stream()
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()))
                .anyMatch(ResourceEntry::isSkill)
                || resourceTemplates.values()
                .stream()
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resourceTemplate()))
                .anyMatch(ResourceTemplateEntry::isSkill);
    }

    @Override
    public List<Resource> skills(McpRequestContext requestContext)
    {
        return resources.values()
                .stream()
                .filter(ResourceEntry::isSkill)
                .map(ResourceEntry::resource)
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .sorted(comparing(Resource::uri))
                .collect(toImmutableList());
    }

    @Override
    public Optional<Skill> skill(McpRequestContext requestContext, String uri)
    {
        return servedSkill(requestContext, uri)
                .map(servedSkill -> buildSkill(requestContext, servedSkill.uri(), servedSkill.kind()));
    }

    private record ServedSkill(String uri, ResourceKind kind) {}

    private Optional<ServedSkill> servedSkill(McpRequestContext requestContext, String uri)
    {
        if (!isValidUri(uri) || !capabilityFilter.isAllowed(requestContext.identity(), uri)) {
            return Optional.empty();
        }

        // URI equality ignores the case of the scheme and host, so continue with the registered spelling, which
        // supporting files are matched against
        ResourceEntry resourceEntry = resources.get(toUri(uri));
        if (resourceEntry != null) {
            return Optional.of(resourceEntry)
                    .filter(entry -> entry.isSkill() && capabilityFilter.isAllowed(requestContext.identity(), entry.resource()))
                    .map(entry -> new ServedSkill(entry.resource().uri(), entry.kind()));
        }

        return findResourceTemplate(uri)
                .map(ResourceTemplateMatch::entry)
                .filter(entry -> entry.isSkill() && capabilityFilter.isAllowed(requestContext.identity(), entry.resourceTemplate()))
                .map(entry -> new ServedSkill(uri, entry.kind()));
    }

    private Skill buildSkill(McpRequestContext requestContext, String uri, ResourceKind kind)
    {
        byte[] skillMarkdown = readFileContent(requestContext, uri)
                .orElseThrow(() -> exception(INTERNAL_ERROR, "Skill %s must have exactly one content item".formatted(uri)));

        Map<String, Object> frontmatter;
        try {
            frontmatter = parseFrontmatter(new String(skillMarkdown, UTF_8));
        }
        catch (IllegalArgumentException e) {
            throw exception(INTERNAL_ERROR, e, "Skill %s is invalid: %s".formatted(uri, e.getMessage()));
        }

        String skillRoot = uri.substring(0, uri.length() - SKILL_MD_FILE.length() - 1);
        String skillName = skillRoot.substring(skillRoot.lastIndexOf('/') + 1);
        if (!skillName.equals(frontmatter.get("name"))) {
            throw exception(INTERNAL_ERROR, "Skill %s frontmatter name does not match its path: %s".formatted(uri, frontmatter.get("name")));
        }

        if ((kind == DYNAMIC_SKILL) || hasUnstableDescendants(requestContext, uri, skillRoot)) {
            return new Skill(uri, frontmatter, SkillResources.Dynamic.DYNAMIC);
        }

        ImmutableList.Builder<SkillResource> files = ImmutableList.builder();
        files.add(skillResource(uri, skillMarkdown));

        // every static resource under the skill's directory, including those of nested skills, is part of the skill
        List<Resource> supportingFiles = descendants(requestContext, uri, skillRoot)
                .map(ResourceEntry::resource)
                .filter(resource -> !resource.mimeType().equals(DIRECTORY_MIME_TYPE))
                .sorted(comparing(Resource::uri))
                .collect(toImmutableList());
        for (Resource supportingFile : supportingFiles) {
            Optional<byte[]> content = readFileContent(requestContext, supportingFile.uri());
            if (content.isEmpty()) {
                // a file whose read doesn't yield exactly one content item has no digest that a host could verify
                return new Skill(uri, frontmatter, SkillResources.Dynamic.DYNAMIC);
            }
            files.add(skillResource(supportingFile.uri(), content.orElseThrow()));
        }

        List<SkillResource> manifest = files.build();
        long totalSize = manifest.stream().mapToLong(SkillResource::size).sum();
        if ((manifest.size() > MAX_SKILL_RESOURCES) || (totalSize > MAX_SKILL_SIZE)) {
            log.warn("Skill %s has %s files totaling %s bytes, exceeding the limits (%s files, %s bytes) that hosts are required to support", uri, manifest.size(), totalSize, MAX_SKILL_RESOURCES, MAX_SKILL_SIZE);
        }

        return new Skill(uri, frontmatter, new SkillResources.Files(manifest));
    }

    // A manifest must list every file with a stable digest, which a nested dynamic skill or a template that serves
    // files below the skill's root makes impossible. Templates are checked regardless of the capability filter, as
    // a read of a static file that a hidden template also matches is rejected.
    private boolean hasUnstableDescendants(McpRequestContext requestContext, String uri, String skillRoot)
    {
        if (descendants(requestContext, uri, skillRoot).anyMatch(entry -> entry.kind() == DYNAMIC_SKILL)) {
            return true;
        }

        Optional<ResourceTemplateEntry> servingTemplate = resources.containsKey(toUri(uri))
                ? Optional.empty()
                : findResourceTemplate(uri).map(ResourceTemplateMatch::entry);

        return resourceTemplates.entrySet()
                .stream()
                .filter(entry -> !isOnlySkillMarkdownBelowRoot(entry, servingTemplate))
                .anyMatch(entry -> canMatchBelow(entry.getKey(), skillRoot + "/"));
    }

    // A skill template's URI ends in the literals "/<name>/SKILL.md". Without explicit regexes each variable matches
    // exactly one segment, so below the root of the skill it serves, the template matches only that SKILL.md.
    private static boolean isOnlySkillMarkdownBelowRoot(Map.Entry<UriTemplate, ResourceTemplateEntry> entry, Optional<ResourceTemplateEntry> servingTemplate)
    {
        return servingTemplate.map(serving -> serving.equals(entry.getValue())).orElse(false)
                && (entry.getKey().getNumberOfExplicitRegexes() == 0);
    }

    private Stream<ResourceEntry> descendants(McpRequestContext requestContext, String uri, String skillRoot)
    {
        return resources.values()
                .stream()
                .filter(entry -> entry.resource().uri().startsWith(skillRoot + "/") && !entry.resource().uri().equals(uri))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()));
    }

    private boolean canMatchBelow(UriTemplate uriTemplate, String prefix)
    {
        // hitEnd() means the matcher ran out of input, i.e. some URI starting with the prefix could still match
        Pattern pattern = requireNonNullElseGet(templatePatterns.get(uriTemplate), () -> Pattern.compile(uriTemplate.getPattern().getRegex()));
        Matcher matcher = pattern.matcher(prefix);
        return matcher.matches() || matcher.hitEnd();
    }

    @Override
    public boolean isSkillNamespaceEnumerable(McpRequestContext requestContext)
    {
        return resourceTemplates.entrySet()
                .stream()
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.getValue().resourceTemplate()))
                .noneMatch(entry -> canMatchBelow(entry.getKey(), SKILL_URI_PREFIX));
    }

    @Override
    public Optional<List<Resource>> resourceChildren(McpRequestContext requestContext, String uri)
    {
        if (!isValidUri(uri) || uri.endsWith("/") || !capabilityFilter.isAllowed(requestContext.identity(), uri)) {
            return Optional.empty();
        }

        ResourceEntry resourceEntry = resources.get(toUri(uri));
        if (resourceEntry != null) {
            boolean isDirectory = resourceEntry.resource().mimeType().equals(DIRECTORY_MIME_TYPE) && capabilityFilter.isAllowed(requestContext.identity(), resourceEntry.resource());
            return isDirectory ? Optional.of(directChildren(requestContext, resourceEntry.resource().uri())) : Optional.empty();
        }

        if (!uri.startsWith(SKILL_URI_PREFIX) || (uri.length() <= SKILL_URI_PREFIX.length())) {
            return Optional.empty();
        }

        List<Resource> children = directChildren(requestContext, uri);
        return children.isEmpty() ? Optional.empty() : Optional.of(children);
    }

    private List<Resource> directChildren(McpRequestContext requestContext, String uri)
    {
        String prefix = uri + "/";
        Map<String, Resource> children = new HashMap<>();

        resources.values()
                .stream()
                .map(ResourceEntry::resource)
                .filter(resource -> resource.uri().startsWith(prefix))
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .forEach(resource -> {
                    String relativePath = resource.uri().substring(prefix.length());
                    int slash = relativePath.indexOf('/');
                    if (slash < 0) {
                        if (!relativePath.isEmpty()) {
                            children.put(resource.uri(), resource);
                        }
                    }
                    else if (slash > 0) {
                        String name = relativePath.substring(0, slash);
                        children.putIfAbsent(prefix + name, directoryResource(prefix + name, name));
                    }
                });

        // a skill template's instances can't be enumerated, but the root of any instance is a directory containing its SKILL.md
        String skillMarkdownUri = prefix + SKILL_MD_FILE;
        if (!children.containsKey(skillMarkdownUri)) {
            findResourceTemplate(skillMarkdownUri)
                    .map(ResourceTemplateMatch::entry)
                    .filter(ResourceTemplateEntry::isSkill)
                    .map(ResourceTemplateEntry::resourceTemplate)
                    .filter(resourceTemplate -> capabilityFilter.isAllowed(requestContext.identity(), resourceTemplate))
                    .ifPresent(resourceTemplate -> children.put(skillMarkdownUri, new Resource(
                            resourceTemplate.name(),
                            skillMarkdownUri,
                            resourceTemplate.description(),
                            resourceTemplate.mimeType(),
                            OptionalLong.empty(),
                            resourceTemplate.annotations(),
                            resourceTemplate.icons(),
                            resourceTemplate.meta())));
        }

        return ImmutableList.copyOf(children.values());
    }

    private static Resource directoryResource(String uri, String name)
    {
        return new Resource(name, uri, Optional.empty(), DIRECTORY_MIME_TYPE, OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private Optional<byte[]> readFileContent(McpRequestContext requestContext, String uri)
    {
        List<ResourceContents> contents = readResourceContents(requestContext, new ReadResourceRequest(uri))
                .flatMap(ReadResourceResult::contents)
                .orElse(ImmutableList.of());
        if (contents.size() != 1) {
            return Optional.empty();
        }

        ResourceContents resourceContents = contents.getFirst();
        if (resourceContents.text().isPresent()) {
            return Optional.of(resourceContents.text().orElseThrow().getBytes(UTF_8));
        }
        try {
            return Optional.of(Base64.getDecoder().decode(resourceContents.blob().orElseThrow()));
        }
        catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static SkillResource skillResource(String uri, byte[] content)
    {
        return new SkillResource(uri, "sha256:" + Hashing.sha256().hashBytes(content), content.length);
    }

    private Optional<ResourceEntry> findResource(String uriString)
    {
        URI uri = toUri(uriString);

        return resources.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(uri))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private record ResourceTemplateMatch(ResourceTemplateEntry entry, ResourceTemplateValues values) {}

    private Optional<ResourceTemplateMatch> findResourceTemplate(String uri)
    {
        return resourceTemplates.entrySet()
                .stream()
                .flatMap(entry -> {
                    UriTemplate uriTemplate = entry.getKey();
                    Map<String, String> variables = new HashMap<>();
                    if (uriTemplate.match(uri, variables)) {
                        return Stream.of(new ResourceTemplateMatch(entry.getValue(), new ResourceTemplateValues(variables)));
                    }
                    return Stream.of();
                })
                .findFirst();
    }

    private static boolean isValidUri(String uriString)
    {
        try {
            URI.create(uriString);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static URI toUri(String uriString)
    {
        try {
            return URI.create(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static UriTemplate toUriTemplate(String uriString)
    {
        try {
            return new UriTemplate(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static String completionKey(CompleteReference reference)
    {
        return switch (reference) {
            case PromptReference(var name, _) -> name;
            case ResourceReference(var uri) -> uri;
        };
    }
}
