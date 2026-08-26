package io.airlift.mcp.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.http.client.HttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.MapApp;
import io.airlift.mcp.McpModule;
import io.airlift.mcp.TestingEndpoints;
import io.airlift.mcp.TestingIdentity;
import io.airlift.mcp.TestingIdentityMapper;
import io.airlift.mcp.TestingServer;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import io.airlift.mcp.storage.MemoryStorageController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class McpClientTestBase
{
    private final TestingServer testingServer;
    private final URI baseUri;
    private final URI uri;
    private final HttpClient httpClient;

    protected McpClientTestBase()
    {
        testingServer = new TestingServer(serverProperties(ImmutableMap.of()), Optional.empty(), mcpModuleApplicator());
        baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl();
        uri = mcpUri(testingServer);
        httpClient = testingServer.httpClient();
    }

    /**
     * The endpoints and bindings every client test server is built from. Exposed so a test needing a server
     * configured differently - see {@link #serverProperties(Map)} - can build one against the same endpoints.
     */
    protected static Function<McpModule.Builder, Module> mcpModuleApplicator()
    {
        return builder -> builder
                // the header based mapper is used - as in TestMcp - so that authorization failures can be tested
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withStorage(binding -> binding.to(MemoryStorageController.class).in(SINGLETON))
                .addIcon("google", binding -> binding.toInstance(new Icon("https://www.gstatic.com/images/branding/searchlogo/ico/favicon.ico")))
                .withAllInClass(TestingEndpoints.class)
                .withAllInClass(MapApp.class)
                .withAllInClass(TestingTaskEndpoints.class)
                .withLegacyBindings().withSessions(binding -> binding.to(StandardSessionController.class).in(SINGLETON))
                // hold the client to the headers the spec requires of it - MCP-Method and Mcp-Name
                .withStrictValidation()
                .build();
    }

    /**
     * The shared server properties, with {@code additionalProperties} overriding them.
     */
    protected static Map<String, String> serverProperties(Map<String, String> additionalProperties)
    {
        // as in TestMcp, GET on /mcp must return 405 rather than opening an event stream; the fast subscription
        // cache period keeps testSubscriptionInterruption from waiting on the 1 minute default
        return ImmutableMap.<String, String>builder()
                .put("mcp.http-get-events.enabled", "false")
                .put("mcp.resource-subscription.cache-period", "100ms")
                .putAll(additionalProperties)
                .buildKeepingLast();
    }

    protected static URI mcpUri(TestingServer server)
    {
        return server.injector().getInstance(TestingHttpServer.class).getBaseUrl().resolve("/mcp");
    }

    @AfterAll
    public void shutdown()
    {
        testingServer.close();
    }

    public HttpClient httpClient()
    {
        return httpClient;
    }

    public URI uri()
    {
        return uri;
    }

    public URI baseUri()
    {
        return baseUri;
    }

    public Injector injector()
    {
        return testingServer.injector();
    }
}
