package io.airlift.mcp.reflection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.mcp.McpApp;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.McpApp.SERVER_DOMAIN;
import static java.util.Objects.requireNonNull;

public class AppResourceHandlerProvider
        implements Provider<ResourceEntry>, ResourceHandler
{
    private static final String PROFILE_MCP_APP = "text/html;profile=mcp-app";

    private final McpApp app;
    private final String name;
    private final Optional<String> description;
    private final Supplier<String> contentSupplier;
    private final int contentLength;
    private volatile String host;
    private volatile Supplier<List<ResourceContents>> resourceContentsSupplier;

    public AppResourceHandlerProvider(McpApp app, String name, Optional<String> description, Supplier<String> contentSupplier, int contentLength)
    {
        this.app = requireNonNull(app, "app is null");
        this.name = requireNonNull(name, "name is null");
        this.description = requireNonNull(description, "description is null");
        this.contentSupplier = requireNonNull(contentSupplier, "contentSupplier is null");
        this.contentLength = contentLength;
    }

    @Inject
    public void setHttpServerInfo(HttpServerInfo httpServerInfo)
    {
        URI baseUri = (httpServerInfo.getHttpsUri() != null) ? httpServerInfo.getHttpsUri() : httpServerInfo.getHttpUri();
        host = baseUri.getHost();
    }

    @Override
    public ResourceEntry get()
    {
        Resource resource = new Resource(
                name,
                app.resourceUri(),
                description,
                PROFILE_MCP_APP,
                OptionalLong.of(contentLength),
                Optional.empty(),
                Optional.empty());

        ImmutableMap.Builder<String, Object> cspMeta = ImmutableMap.builder();
        applyDomains(cspMeta, "connectDomains", app.connectDomains());
        applyDomains(cspMeta, "resourceDomains", app.resourceDomains());
        applyDomains(cspMeta, "frameDomains", app.frameDomains());
        applyDomains(cspMeta, "baseUriDomains", app.baseUriDomains());

        ImmutableMap.Builder<String, Object> permissionsMeta = ImmutableMap.builder();
        applyObject(permissionsMeta, "camera", app.requestCameraPermission());
        applyObject(permissionsMeta, "microphone", app.requestMicrophonePermission());
        applyObject(permissionsMeta, "geolocation", app.requestGeolocationPermission());
        applyObject(permissionsMeta, "clipboardWrite", app.requestClipboardWritePermission());

        ImmutableMap.Builder<String, Object> uiMeta = ImmutableMap.builder();
        applyDomain(uiMeta, "domain", app.sandboxOriginDomain());
        if (app.prefersBorder() != OptionalBoolean.UNDEFINED) {
            uiMeta.put("prefersBorder", app.prefersBorder() == OptionalBoolean.TRUE);
        }
        uiMeta.put("csp", cspMeta.build());
        uiMeta.put("permissions", permissionsMeta.build());

        ImmutableMap.Builder<String, Object> meta = ImmutableMap.builder();
        meta.put("ui", uiMeta.build());

        this.resourceContentsSupplier = () -> ImmutableList.of(new ResourceContents(resource.uri(), resource.uri(), resource.mimeType(), contentSupplier.get()).withMeta(meta.build()));

        return new ResourceEntry(resource, this);
    }

    @Override
    public List<ResourceContents> readResource(McpRequestContext requestContext, Resource sourceResource, ReadResourceRequest readResourceRequest)
    {
        return requireNonNull(resourceContentsSupplier, "resourceContents is null").get();
    }

    private void applyObject(ImmutableMap.Builder<String, Object> map, String field, boolean value)
    {
        if (value) {
            map.put(field, ImmutableMap.of());
        }
    }

    private void applyDomains(ImmutableMap.Builder<String, Object> map, String field, String[] domains)
    {
        if (domains.length == 0) {
            return;
        }

        Set<String> mappedDomains = Stream.of(domains).map(this::mapDomain).collect(toImmutableSet());
        map.put(field, mappedDomains);
    }

    @SuppressWarnings("SameParameterValue")
    private void applyDomain(ImmutableMap.Builder<String, Object> map, String field, String domain)
    {
        if (!domain.isEmpty()) {
            map.put(field, mapDomain(domain));
        }
    }

    private String mapDomain(String domain)
    {
        return domain.replaceAll(SERVER_DOMAIN, requireNonNull(host, "host is null"));
    }
}
