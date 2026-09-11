package io.airlift.mcp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ConformanceTestRunner
{
    private static final String CONFORMANCE_PACKAGE = "@modelcontextprotocol/conformance@0.2.0-alpha.11";

    private final String mcpUri;

    public ConformanceTestRunner(String mcpUri)
    {
        this.mcpUri = requireNonNull(mcpUri, "mcpUri is null");

        try {
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

    public String runTest(String scenario)
    {
        // see: https://github.com/modelcontextprotocol/conformance?tab=readme-ov-file#testing-servers
        try {
            Process process = new ProcessBuilder("npm", "exec", "--yes", CONFORMANCE_PACKAGE, "--", "server", "--url", mcpUri, "--scenario", scenario, "--verbose")
                    .start();
            process.waitFor();
            String result = process.inputReader(UTF_8).readAllAsString();
            return result + process.errorReader(UTF_8).readAllAsString();
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
