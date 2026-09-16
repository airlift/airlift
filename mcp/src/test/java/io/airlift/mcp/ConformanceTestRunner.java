package io.airlift.mcp;

import com.google.common.io.Resources;

import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ConformanceTestRunner
{
    private static final String CONFORMANCE_PACKAGE = "@modelcontextprotocol/conformance@0.2.0-alpha.11";
    private static final String EXPECTED_FAILURES_FILE = "conformance-expected-failures.yaml";

    private final String mcpUri;
    private final Path expectedFailures;

    /**
     * @param exitCode the conformance runner's verdict: zero when every failing check is listed
     *         in the expected-failures file - and every listed check still fails
     */
    public record Result(int exitCode, String output)
    {
        public Result
        {
            requireNonNull(output, "output is null");
        }
    }

    public ConformanceTestRunner(String mcpUri)
    {
        this.mcpUri = requireNonNull(mcpUri, "mcpUri is null");

        try {
            expectedFailures = Path.of(Resources.getResource(EXPECTED_FAILURES_FILE).toURI());

            int status = new ProcessBuilder("npm", "install", "-g", CONFORMANCE_PACKAGE)
                    .start()
                    .waitFor();
            if (status != 0) {
                throw new RuntimeException("Failed to install conformance");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Result runTest(String scenario)
    {
        // see: https://github.com/modelcontextprotocol/conformance?tab=readme-ov-file#testing-servers
        try {
            Process process = new ProcessBuilder(
                    "npm",
                    "exec",
                    "--yes",
                    CONFORMANCE_PACKAGE,
                    "--",
                    "server",
                    "--url",
                    mcpUri,
                    "--scenario",
                    scenario,
                    "--expected-failures",
                    expectedFailures.toString(),
                    "--verbose")
                    .start();
            int exitCode = process.waitFor();
            String output = process.inputReader(UTF_8).readAllAsString() + process.errorReader(UTF_8).readAllAsString();
            return new Result(exitCode, output);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
