package io.airlift.mcp;

import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.UiToolVisibility;

import static io.airlift.mcp.model.OptionalBoolean.UNDEFINED;

public @interface McpApp
{
    // These variables are substituted by the MCP server at runtime. They can be used in any domain field of this annotation.
    // e.g. domain = "__HOST__" will be replaced with the actual server domain.
    String SERVER_DOMAIN = "__HOST__";

    // reference to the UI's root file. Airlift will use {@link com.google.common.io.Resources.getResource(String)} to load this file from the classpath
    String sourcePath();

    // Airlift will create the MCP resource - do not create it yourself
    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#resource-discovery - McpUiToolMeta.resourceUri
    String resourceUri();

    // if true, reloads the file at {@link #sourcePath()} on every tool call, instead of caching it. Useful for development, but should be false in production.
    boolean debugMode() default false;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#resource-discovery - McpUiToolMeta.visibility
    UiToolVisibility[] visibility() default {};

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.domain
    String sandboxOriginDomain() default "";

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.prefersBorder
    OptionalBoolean prefersBorder() default UNDEFINED;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.permissions.camera
    boolean requestCameraPermission() default false;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.permissions.microphone
    boolean requestMicrophonePermission() default false;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.permissions.geolocation
    boolean requestGeolocationPermission() default false;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - UIResourceMeta.permissions.clipboardWrite
    boolean requestClipboardWritePermission() default false;

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - McpUiResourceCsp.connectDomains
    String[] connectDomains() default {};

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - McpUiResourceCsp.resourceDomains
    String[] resourceDomains() default {};

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - McpUiResourceCsp.frameDomains
    String[] frameDomains() default {};

    // see https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx#ui-resource-format - McpUiResourceCsp.baseUriDomains
    String[] baseUriDomains() default {};
}
