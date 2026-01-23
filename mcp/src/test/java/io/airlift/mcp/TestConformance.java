package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Scopes;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.sessions.MemorySessionController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestConformance
{
    private static final List<String> SCENARIOS = List.of(
            "server-initialize",
            "logging-set-level",
            "ping",
            "completion-complete",
            "tools-list",
            "tools-call-simple-text",
            "tools-call-image",
            "tools-call-audio",
            "tools-call-embedded-resource",
            "tools-call-mixed-content",
            "tools-call-with-logging",
            "tools-call-error",
            "tools-call-with-progress",
            "tools-call-sampling",
            "tools-call-elicitation",
            "server-sse-multiple-streams",
            "resources-list",
            "resources-read-text",
            "resources-read-binary",
            "resources-templates-read",
            "resources-subscribe",
            "resources-unsubscribe",
            "prompts-list",
            "prompts-get-simple",
            "prompts-get-with-args",
            "prompts-get-embedded-resource",
            "prompts-get-with-image",
            "tools-call-elicitation");

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

    @ParameterizedTest
    @MethodSource("scenarioProvider")
    public void testConformance(String scenario)
    {
        // see: https://github.com/modelcontextprotocol/conformance?tab=readme-ov-file#testing-servers
        String result = nodeContainer.execute("npx", "--yes", "@modelcontextprotocol/conformance", "server", "--url", mcpUri, "--scenario", scenario);
        assertThat(result).contains("Passed: 1/1, 0 failed, 0 warnings");
    }

    static List<String> scenarioProvider()
    {
        return SCENARIOS;
    }
}
