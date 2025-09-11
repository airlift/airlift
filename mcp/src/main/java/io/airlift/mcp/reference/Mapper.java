package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.Annotations;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.google.common.collect.ImmutableList.toImmutableList;

interface Mapper
{
    static McpSchema.Content mapContent(Content ourContent)
    {
        return switch (ourContent) {
            case Content.TextContent(var text, var annotations) -> new McpSchema.TextContent(annotations.map(Mapper::mapAnnotations).orElse(null), text);

            case Content.ImageContent(var data, var mimeType, var annotations) ->
                    new McpSchema.ImageContent(annotations.map(Mapper::mapAnnotations).orElse(null), data, mimeType);

            case Content.AudioContent(var data, var mimeType, var annotations) ->
                    new McpSchema.AudioContent(annotations.map(Mapper::mapAnnotations).orElse(null), data, mimeType);

            case Content.ResourceLink(var name, var uri, var description, var mimeType, var size, var annotations) ->
                    new McpSchema.ResourceLink(name, null, uri, description.orElse(null), mimeType, size.isPresent() ? size.getAsLong() : null, annotations.map(Mapper::mapAnnotations).orElse(null), ImmutableMap.of());

            case Content.EmbeddedResource(var resourceContents, var annotations) -> {
                McpSchema.ResourceContents theirResourceContents = mapResourceContents(resourceContents);
                yield new McpSchema.EmbeddedResource(annotations.map(Mapper::mapAnnotations).orElse(null), theirResourceContents);
            }
        };
    }

    static McpSchema.ResourceContents mapResourceContents(ResourceContents ourResourceContents)
    {
        return ourResourceContents.blob().map(blobValue -> (McpSchema.ResourceContents) new McpSchema.BlobResourceContents(ourResourceContents.uri(), ourResourceContents.mimeType(), blobValue))
                .orElseGet(() -> ourResourceContents.text().map(textValue -> new McpSchema.TextResourceContents(ourResourceContents.uri(), ourResourceContents.mimeType(), textValue))
                        .orElseGet(() -> new McpSchema.TextResourceContents(ourResourceContents.uri(), ourResourceContents.mimeType(), "")));
    }

    static McpSchema.Annotations mapAnnotations(Annotations ourAnnotations)
    {
        List<McpSchema.Role> audience = ourAnnotations.audience()
                .stream()
                .map(Mapper::mapRole)
                .collect(toImmutableList());
        return new McpSchema.Annotations(audience, ourAnnotations.priority().orElse(0.0));
    }

    static McpSchema.Role mapRole(Role ourRole)
    {
        return McpSchema.Role.valueOf(ourRole.name().toUpperCase());
    }

    static McpStatelessServerFeatures.SyncResourceSpecification mapResource(Resource ourResource, ResourceHandler ourHandler)
    {
        McpSchema.Resource.Builder theirResourceBuilder = McpSchema.Resource.builder()
                .name(ourResource.name())
                .uri(ourResource.uri());

        ourResource.description().ifPresent(theirResourceBuilder::description);

        ourResource.annotations().ifPresent(annotations -> {
            McpSchema.Annotations theirAnnotations = mapAnnotations(annotations);
            theirResourceBuilder.annotations(theirAnnotations);
        });

        theirResourceBuilder.mimeType(ourResource.mimeType());

        ourResource.size().ifPresent(theirResourceBuilder::size);

        BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler = ((context, theirReadResourceRequest) -> {
            HttpServletRequest request = (HttpServletRequest) context.get(McpMetadata.CONTEXT_REQUEST_KEY);
            ReadResourceRequest readResourceRequest = new ReadResourceRequest(theirReadResourceRequest.uri(), Optional.ofNullable(theirReadResourceRequest.meta()));

            List<ResourceContents> ourResourceContents = ourHandler.readResource(request, ourResource, readResourceRequest);

            List<McpSchema.ResourceContents> theirResourceContents = ourResourceContents.stream()
                    .map(Mapper::mapResourceContents)
                    .collect(toImmutableList());

            return new McpSchema.ReadResourceResult(theirResourceContents);
        });

        return new McpStatelessServerFeatures.SyncResourceSpecification(theirResourceBuilder.build(), exceptionSafeHandler(handler));
    }

    static <T, R> BiFunction<McpTransportContext, T, R> exceptionSafeHandler(BiFunction<McpTransportContext, T, R> handler)
    {
        return (context, request) -> {
            try {
                return handler.apply(context, request);
            }
            catch (Exception e) {
                if (e instanceof McpException mcpException) {
                    // this will improve if the MCP reference team accepts our PR: https://github.com/modelcontextprotocol/java-sdk/pull/465
                    JsonRpcErrorDetail errorDetail = mcpException.errorDetail();
                    throw new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(errorDetail.code(), errorDetail.message(), errorDetail.data()));
                }
                throw e;
            }
        };
    }

    static McpStatelessServerFeatures.SyncPromptSpecification mapPrompt(Prompt ourPrompt, PromptHandler ourHandler)
    {
        List<McpSchema.PromptArgument> ourArguments = ourPrompt
                .arguments()
                .stream()
                .map(Mapper::mapPromptArgument)
                .collect(toImmutableList());

        McpSchema.Prompt theirPrompt = new McpSchema.Prompt(ourPrompt.name(),
                null,
                ourPrompt.description().orElse(null),
                ourArguments);

        BiFunction<McpTransportContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler = (context, theirGetPromptRequest) -> {
            HttpServletRequest request = (HttpServletRequest) context.get(McpMetadata.CONTEXT_REQUEST_KEY);
            GetPromptRequest getPromptRequest = new GetPromptRequest(theirGetPromptRequest.name(), theirGetPromptRequest.arguments(), Optional.ofNullable(theirGetPromptRequest.meta()));

            GetPromptResult promptResult = ourHandler.getPrompt(request, getPromptRequest);

            List<McpSchema.PromptMessage> promptMessages = promptResult.messages()
                    .stream()
                    .map(Mapper::mapPromptMessage)
                    .collect(toImmutableList());

            return new McpSchema.GetPromptResult(promptResult.description().orElse(null), promptMessages);
        };

        return new McpStatelessServerFeatures.SyncPromptSpecification(theirPrompt, exceptionSafeHandler(handler));
    }

    static McpSchema.PromptMessage mapPromptMessage(GetPromptResult.PromptMessage ourPromptMessage)
    {
        return new McpSchema.PromptMessage(mapRole(ourPromptMessage.role()), mapContent(ourPromptMessage.content()));
    }

    static McpSchema.PromptArgument mapPromptArgument(Prompt.Argument ourArgument)
    {
        return new McpSchema.PromptArgument(ourArgument.name(), ourArgument.description().orElse(null), ourArgument.required());
    }

    static McpStatelessServerFeatures.SyncToolSpecification mapTool(ObjectMapper objectMapper, Tool ourTool, ToolHandler<?> ourHandler)
    {
        try {
            McpSchema.Tool.Builder theirToolBuilder = McpSchema.Tool.builder()
                    .name(ourTool.name())
                    .inputSchema(objectMapper.writeValueAsString(ourTool.inputSchema()));
            if (ourTool.outputSchema().isPresent()) {
                theirToolBuilder.outputSchema(objectMapper.writeValueAsString(ourTool.outputSchema().get()));
            }

            ourTool.description().ifPresent(theirToolBuilder::description);

            McpSchema.ToolAnnotations theirToolAnnotations = new McpSchema.ToolAnnotations(
                    ourTool.annotations().title().orElse(null),
                    ourTool.annotations().readOnlyHint().orElse(null),
                    ourTool.annotations().destructiveHint().orElse(null),
                    ourTool.annotations().idempotentHint().orElse(null),
                    ourTool.annotations().openWorldHint().orElse(null),
                    ourTool.annotations().returnDirect().orElse(null));
            theirToolBuilder.annotations(theirToolAnnotations);

            BiFunction<McpTransportContext, McpSchema.CallToolRequest, McpSchema.CallToolResult> callHandler = ((context, theirCallToolRequest) -> {
                HttpServletRequest request = (HttpServletRequest) context.get(McpMetadata.CONTEXT_REQUEST_KEY);
                CallToolRequest callToolRequest = new CallToolRequest(theirCallToolRequest.name(), theirCallToolRequest.arguments(), Optional.ofNullable(theirCallToolRequest.meta()));

                CallToolResult<?> callToolResult = ourHandler.callTool(request, callToolRequest);

                List<McpSchema.Content> theirContent = callToolResult.content()
                        .stream()
                        .map(Mapper::mapContent)
                        .collect(toImmutableList());

                Map<String, Object> theirStructuredContent = callToolResult.structuredContent()
                        .map(ourStructuredContent -> mapStructuredContent(objectMapper, ourStructuredContent))
                        .orElse(null);

                return new McpSchema.CallToolResult(theirContent, callToolResult.isError(), theirStructuredContent);
            });

            return McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(theirToolBuilder.build())
                    .callHandler(exceptionSafeHandler(callHandler))
                    .build();
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException("Could not serialize tool's schema: " + ourTool, e);
        }
    }

    static Map<String, Object> mapStructuredContent(ObjectMapper objectMapper, StructuredContent<?> ourStructuredContent)
    {
        return objectMapper.convertValue(ourStructuredContent, new TypeReference<>() {});
    }
}
