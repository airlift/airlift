[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - quick start

## Create tools, prompts, resources as needed

#### Tools

```java
// in some class...

@McpTool(name = "add", description = "Adds two numbers")
public int addTwoNumbers(
        @McpDescription("first number to add") int a,
        @McpDescription("second number to add") int b)
{
    return a + b;
}
```

#### Prompts

```java
// in some class...

@McpPrompt(name = "greeting", description = "Generate a greeting message")
public String greeting(@McpDescription("Name of the person to greet") String name)
{
    return "Hello, " + name + "!";
}
```

#### Resources, completions, etc.

For resources, completions, etc. please see the [MCP Server documentation](../README.md#details).

## Add the MCP server Guice module

```java
Module module = McpModule.builder()
    .addAllInClass(MyClassWithToolsPromptsEtc.class)
    .addAllInClass(MyOtherClassWithToolsPromptsEtc.class)
    .build();

// in your main module, etc.
binder.install(module);
```

## Test your MCP server

_Run your application locally via standard Airlift Bootstrap and make note of the server port._

Start the MCP testing tool:
```shell
npx @modelcontextprotocol/inspector
```

A browser should open with the MCP Inspector tool. Set the "Transport Type" to 
"Streamable HTTP". Change the URL to `http://localhost:<server-port>/mcp` and click "Connect".
