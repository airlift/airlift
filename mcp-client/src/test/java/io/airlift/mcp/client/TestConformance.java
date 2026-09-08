package io.airlift.mcp.client;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.google.common.io.Resources;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestConformance
{
    private static final List<String> SCENARIOS = ImmutableList.of(
            "initialize",
            "tools_call",
            "request-metadata",
            "sep-2322-client-request-state",
            "http-standard-headers",
            "json-schema-ref-no-deref",
            "json-schema-2020-12-preservation");

    private final Closer closer = Closer.create();
    private final ConformanceTestRunner conformanceTestRunner;
    private final String conformanceScriptPath;

    public TestConformance()
    {
        conformanceTestRunner = closer.register(new ConformanceTestRunner());

        conformanceScriptPath = Resources.getResource("conformance.sh").getPath();
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
            throws Exception
    {
        Process process = new ProcessBuilder()
                .command("npx", "--yes", "@modelcontextprotocol/conformance@0.2.0-alpha.11", "client", "--command", conformanceScriptPath + " localhost:" + conformanceTestRunner.getPort(), "--scenario", scenario)
                .start();
        process.waitFor();
        String result = process.inputReader().readAllAsString() + "\n" + process.errorReader().readAllAsString();
        assertThat(result).contains("0 failed, 0 warnings");
    }

    static List<String> scenarioProvider()
    {
        return SCENARIOS;
    }
}
