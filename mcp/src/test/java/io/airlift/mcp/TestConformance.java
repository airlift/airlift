package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Scopes;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.sessions.MemorySessionController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Optional;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestConformance
{
    private final Closer closer = Closer.create();
    private final TestingNodeContainer nodeContainer;
    private final String mcpUri;

    public TestConformance()
    {
        nodeContainer = closer.register(new TestingNodeContainer());

        TestingServer testingServer = closer.register(new TestingServer(ImmutableMap.of(), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance((_) -> authenticated(new TestingIdentity("Mr. Tester"))))
                .withSessions(binding -> binding.to(MemorySessionController.class).in(Scopes.SINGLETON))
                .withAllInClass(ConformanceEndpoints.class)
                .build()));

        mcpUri = testingServer.injector()
                .getInstance(TestingHttpServer.class)
                .getBaseUrl()
                .resolve("/mcp")
                .toString();
    }

    @AfterAll
    public void tearDown()
            throws Exception
    {
        closer.close();
    }

    @Test
    public void testConformance()
    {
        // see: https://github.com/modelcontextprotocol/conformance?tab=readme-ov-file#testing-servers
        String result = nodeContainer.execute("npx", "--yes", "@modelcontextprotocol/conformance", "server", "--url", mcpUri);
        assertThat(result).contains("Total: 26 passed, 0 failed");
    }

    @Test
    public void testConformanceElicitation()
    {
        // see: https://github.com/modelcontextprotocol/conformance?tab=readme-ov-file#testing-servers
        String result = nodeContainer.execute("npx", "--yes", "@modelcontextprotocol/conformance", "server", "--scenario", "tools-call-elicitation", "--url", mcpUri);
        assertThat(result).contains("Passed: 1/1, 0 failed, 0 warnings");
    }
}
