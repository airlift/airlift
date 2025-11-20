package io.airlift.mcp.reference;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateHandler;
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
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.reference.ReferenceServerTransport.CONTEXT_MESSAGE_WRITER_KEY;

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

    static McpRequestContext buildMcpRequestContext(McpTransportContext mcpTransportContext, Optional<Map<String, Object>> meta, RequestContextProvider requestContextProvider, MessageWriter messageWriter)
    {
        Optional<Object> progressToken = meta.flatMap(m -> switch (m.get("progressToken")) {
            case null -> Optional.empty();
            case Number number -> Optional.of(number.longValue());
            case Object obj -> Optional.of(String.valueOf(obj));
        });

        HttpServletRequest request = (HttpServletRequest) mcpTransportContext.get(McpMetadata.CONTEXT_REQUEST_KEY);
        return requestContextProvider.build(request, messageWriter, progressToken);
    }

    static MessageWriter messageWriterFromContext(McpTransportContext mcpTransportContext)
    {
        return Optional.ofNullable(mcpTransportContext.get(CONTEXT_MESSAGE_WRITER_KEY))
                .map(MessageWriter.class::cast)
                .orElseThrow(() -> new IllegalStateException("MessageWriter not found in transport context"));
    }

    static McpStatelessServerFeatures.SyncResourceSpecification mapResource(RequestContextProvider requestContextProvider, Resource ourResource, ResourceHandler ourHandler)
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
            ReadResourceRequest readResourceRequest = new ReadResourceRequest(theirReadResourceRequest.uri(), Optional.ofNullable(theirReadResourceRequest.meta()));
            McpRequestContext mcpRequestContext = buildMcpRequestContext(context, Optional.ofNullable(theirReadResourceRequest.meta()), requestContextProvider, messageWriterFromContext(context));
            List<ResourceContents> ourResourceContents = ourHandler.readResource(mcpRequestContext, ourResource, readResourceRequest);

            List<McpSchema.ResourceContents> theirResourceContents = ourResourceContents.stream()
                    .map(Mapper::mapResourceContents)
                    .collect(toImmutableList());

            return new McpSchema.ReadResourceResult(theirResourceContents);
        });

        return new McpStatelessServerFeatures.SyncResourceSpecification(theirResourceBuilder.build(), exceptionSafeHandler(handler));
    }

    static McpStatelessServerFeatures.SyncResourceTemplateSpecification mapResourceTemplate(RequestContextProvider requestContextProvider, ResourceTemplate ourResourceTemplate, ResourceTemplateHandler ourHandler, Function<String, Map<String, String>> templateValuesMapper)
    {
        McpSchema.ResourceTemplate.Builder theirResourceTemplateBuilder = McpSchema.ResourceTemplate.builder()
                .name(ourResourceTemplate.name())
                .uriTemplate(ourResourceTemplate.uriTemplate());

        ourResourceTemplate.description().ifPresent(theirResourceTemplateBuilder::description);

        ourResourceTemplate.annotations().ifPresent(annotations -> {
            McpSchema.Annotations theirAnnotations = mapAnnotations(annotations);
            theirResourceTemplateBuilder.annotations(theirAnnotations);
        });

        theirResourceTemplateBuilder.mimeType(ourResourceTemplate.mimeType());

        BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler = ((context, theirReadResourceRequest) -> {
            ReadResourceRequest readResourceRequest = new ReadResourceRequest(theirReadResourceRequest.uri(), Optional.ofNullable(theirReadResourceRequest.meta()));
            McpRequestContext requestContext = buildMcpRequestContext(context, Optional.ofNullable(theirReadResourceRequest.meta()), requestContextProvider, messageWriterFromContext(context));

            Map<String, String> templateValues = templateValuesMapper.apply(readResourceRequest.uri());
            List<ResourceContents> ourResourceContents = ourHandler.readResourceTemplate(requestContext, ourResourceTemplate, readResourceRequest, new ResourceTemplateValues(templateValues));

            List<McpSchema.ResourceContents> theirResourceContents = ourResourceContents.stream()
                    .map(Mapper::mapResourceContents)
                    .collect(toImmutableList());

            return new McpSchema.ReadResourceResult(theirResourceContents);
        });

        return new McpStatelessServerFeatures.SyncResourceTemplateSpecification(theirResourceTemplateBuilder.build(), exceptionSafeHandler(handler));
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

    static McpStatelessServerFeatures.SyncPromptSpecification mapPrompt(RequestContextProvider requestContextProvider, Prompt ourPrompt, PromptHandler ourHandler)
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
            GetPromptRequest getPromptRequest = new GetPromptRequest(theirGetPromptRequest.name(), theirGetPromptRequest.arguments(), Optional.ofNullable(theirGetPromptRequest.meta()));
            McpRequestContext requestContext = buildMcpRequestContext(context, Optional.ofNullable(theirGetPromptRequest.meta()), requestContextProvider, messageWriterFromContext(context));

            GetPromptResult promptResult = ourHandler.getPrompt(requestContext, getPromptRequest);

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

    static McpStatelessServerFeatures.SyncToolSpecification mapTool(RequestContextProvider requestContextProvider, McpJsonMapper objectMapper, Tool ourTool, ToolHandler ourHandler)
    {
        try {
            McpSchema.Tool.Builder theirToolBuilder = McpSchema.Tool.builder()
                    .name(ourTool.name())
                    .inputSchema(objectMapper, objectMapper.writeValueAsString(ourTool.inputSchema()));
            if (ourTool.outputSchema().isPresent()) {
                theirToolBuilder.outputSchema(objectMapper, objectMapper.writeValueAsString(ourTool.outputSchema().get()));
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
                CallToolRequest callToolRequest = new CallToolRequest(theirCallToolRequest.name(), theirCallToolRequest.arguments(), Optional.ofNullable(theirCallToolRequest.meta()), Optional.empty());
                McpRequestContext requestContext = buildMcpRequestContext(context, Optional.ofNullable(theirCallToolRequest.meta()), requestContextProvider, messageWriterFromContext(context));

                CallToolResult callToolResult = ourHandler.callTool(requestContext, callToolRequest);

                List<McpSchema.Content> theirContent = callToolResult.content()
                        .stream()
                        .map(Mapper::mapContent)
                        .collect(toImmutableList());

                Map<String, Object> theirStructuredContent = callToolResult.structuredContent()
                        .map(ourStructuredContent -> mapStructuredContent(objectMapper, ourStructuredContent))
                        .orElse(null);

                McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                        .isError(callToolResult.isError())
                        .content(theirContent);
                if (theirStructuredContent != null) {
                    builder.structuredContent(theirStructuredContent);
                }
                return builder.build();
            });

            return McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(theirToolBuilder.build())
                    .callHandler(exceptionSafeHandler(callHandler))
                    .build();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not serialize tool's schema: " + ourTool, e);
        }
    }

    static Map<String, Object> mapStructuredContent(McpJsonMapper objectMapper, StructuredContent<?> ourStructuredContent)
    {
        return objectMapper.convertValue(ourStructuredContent, new TypeRef<>() {});
    }
}
