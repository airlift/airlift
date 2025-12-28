package io.airlift.mcp;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.StructuredContent;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.mcp.model.UiToolVisibility.APP;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.util.Objects.requireNonNull;

// see https://github.com/modelcontextprotocol/ext-apps/blob/main/examples/debug-server/server.ts
// debug-app.html built from this source
// this class ported from server.ts with help from Claude
public class DebugApp
{
    private final AtomicInteger callCounter = new AtomicInteger();
    private final File logFile;
    private final ObjectMapper objectMapper;

    @Inject
    public DebugApp(ObjectMapper objectMapper)
            throws IOException
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        logFile = File.createTempFile("debug-app-log", ".txt");
    }

    public enum ContentType
    {
        text,
        image,
        audio,
        resource,
        resourceLink,
        mixed
    }

    public record DebugInput(
            ContentType contentType,
            boolean multipleBlocks,
            boolean includeStructuredContent,
            boolean includeMeta,
            Optional<String> largeInput,
            boolean simulateError,
            int delayMs)
    {
    }

    @PreDestroy
    public void cleanup()
            throws IOException
    {
        if (logFile.exists()) {
            Files.delete(logFile.toPath());
        }
    }

    public record DebugStructuredContent(DebugInput config, String timestamp, int counter, OptionalInt largeInputLength)
    {
    }

    @McpTool(
            name = "debug-tool",
            description = "Comprehensive debug tool for testing MCP Apps SDK. Configure content types, error simulation, delays, and more.",
            app = @McpApp(
                    resourceUri = "ui://debug-tool/mcp-app.html",
                    sourcePath = "debug-app.html"))
    public CallToolResult debugApp(@McpDefaultValue("text") ContentType contentType,
            @McpDefaultValue("true") boolean multipleBlocks,
            @McpDefaultValue("true") boolean includeStructuredContent,
            @McpDefaultValue("true") boolean includeMeta,
            Optional<String> largeInput,
            boolean simulateError,
            Optional<Integer> delayMs)
            throws InterruptedException
    {
        if (delayMs.isPresent()) {
            TimeUnit.MILLISECONDS.sleep(delayMs.orElseThrow());
        }

        List<Content> content = buildContent(contentType, multipleBlocks);

        CallToolResult callToolResult = new CallToolResult(content);

        if (includeStructuredContent) {
            DebugInput debugInput = new DebugInput(contentType, multipleBlocks, includeStructuredContent, includeMeta, largeInput, simulateError, delayMs.orElse(0));
            DebugStructuredContent debugStructuredContent = new DebugStructuredContent(debugInput, Instant.now().toString(), callCounter.incrementAndGet(), largeInput.map(String::length).map(OptionalInt::of).orElse(OptionalInt.empty()));
            StructuredContent<DebugStructuredContent> structuredContent = new StructuredContent<>(debugStructuredContent);
            callToolResult = new CallToolResult(content, Optional.of(structuredContent), false);
        }

        if (includeMeta) {
            callToolResult = callToolResult.withMeta(ImmutableMap.of("processedAt", Instant.now(), "serverVersion", "1.0.0"));
        }

        if (simulateError) {
            callToolResult = new CallToolResult(Optional.of(content), callToolResult.structuredContent(), Optional.empty(), Optional.of(true), callToolResult.meta());
        }

        return callToolResult;
    }

    public record RefreshResult(String timestamp, int counter)
    {
    }

    @McpTool(
            name = "debug-refresh",
            description = "App-only tool for polling server state. Not visible to the model.",
            app = @McpApp(
                    resourceUri = "ui://debug-tool/mcp-app.html",
                    sourcePath = "debug-app.html",
                    visibility = APP))
    public CallToolResult debugRefresh()
    {
        Instant now = Instant.now();
        TextContent textContent = new TextContent("Server timestamp: " + now);
        StructuredContent<RefreshResult> structuredContent = new StructuredContent<>(new RefreshResult(now.toString(), callCounter.get()));

        return new CallToolResult(ImmutableList.of(textContent), Optional.of(structuredContent), false);
    }

    public record LogPayload(@JsonValue Map<String, Object> payload)
    {
    }

    public record Logged(boolean logged, String logFile)
    {
    }

    @McpTool(
            name = "debug-log",
            description = "App-only tool for logging events to the server log file. Not visible to the model.",
            app = @McpApp(
                    resourceUri = "ui://debug-tool/mcp-app.html",
                    sourcePath = "debug-app.html",
                    visibility = APP))
    public CallToolResult debugLog(String type, LogPayload payload)
    {
        appendToLogFile(Instant.now().toString(), type, payload.payload);

        TextContent textContent = new TextContent("Logged to " + logFile.getAbsolutePath());
        StructuredContent<Logged> structuredContent = new StructuredContent<>(new Logged(true, logFile.getAbsolutePath()));

        return new CallToolResult(ImmutableList.of(textContent), Optional.of(structuredContent), false);
    }

    // Minimal 1x1 blue PNG (base64)
    private static final String BLUE_PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPj/HwADBwIAMCbHYQAAAABJRU5ErkJggg==";

    // Minimal silent WAV (base64) - 44 byte header + 1 sample
    private static final String SILENT_WAV = "UklGRiYAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQIAAAAAAA==";

    private static List<Content> buildContent(ContentType contentType, boolean multipleBlocks)
    {
        if (contentType == ContentType.mixed) {
            multipleBlocks = false;
        }

        int count = multipleBlocks ? 3 : 1;
        List<Content> content = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String suffix = multipleBlocks ? " #" + (i + 1) : "";

            switch (contentType) {
                case text -> content.add(new TextContent("Debug text content" + suffix));
                case image -> content.add(new ImageContent(BLUE_PNG_1X1, "image/png"));
                case audio -> content.add(new AudioContent(SILENT_WAV, "audio/wav"));
                case resource ->
                        content.add(new EmbeddedResource(new ResourceContents("resource", "debug://embedded-resource" + suffix.replace(" ", "-"), "text/plain", "Embedded resource content" + suffix), Optional.empty()));
                case resourceLink ->
                        content.add(new Content.ResourceLink("Linked Resource" + suffix, "debug://linked-resource" + suffix.replace(" ", "-"), Optional.empty(), "text/plain", OptionalLong.empty(), Optional.empty()));
                case mixed -> {
                    content.add(new TextContent("Mixed content: text block"));
                    content.add(new ImageContent(BLUE_PNG_1X1, "image/png"));
                    content.add(new AudioContent(SILENT_WAV, "audio/wav"));
                }
            }
        }

        return content;
    }

    private void appendToLogFile(String timestamp, String type, Object payload)
    {
        try {
            Map<String, Object> data = ImmutableMap.of("timestamp", timestamp, "type", type, "payload", payload);
            Files.writeString(logFile.toPath(), objectMapper.writeValueAsString(data), APPEND);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
