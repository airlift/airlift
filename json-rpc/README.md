[◀︎ Airlift](../README.md)

# JSON-RPC support

This module provides support for creating JSON-RPC endpoints in Airlift.
The endpoints are defined in a similar way to JAX-RS endpoints, by
annotating methods. These JSON-RPC endpoints integrate into JAX-RS via the
`InternalRpcFilter` which adjusts the incoming request URI, headers, etc. such
that JAX-RS sends the request directly to the annotated method. All usual
JAX-RS behavior works as expected, `@Context` parameters, object payloads,
etc. The method can return a Java object, `void` or a JAX-RS `Response`.

JSON-RPC endpoints are exposed via a single URL defined by the base-path, which
by default is `jsonrpc` (note: it can contain any number of `/`). So, HTTP POST 
requests are sent to: `http(s)://host(:port)/basePath`

## Defining JSON-RPC endpoints

JSON-RPC endpoints can be defined declaratively via an annotation or
programmatically.

### Declarative definition

Use the `@JsonRpc` annotation to declare a JSON-RPC endpoint. E.g.

```java
// define a JSON-RPC endpoint with a "method" of "readRecord"
// no-arg version of @JsonRpc uses the Java method name

@JsonRpc
public Response readRecord(@Context Identity myIdentity, RecordRequest request)
{
    // ...
}
```

```java
// define a JSON-RPC endpoint with a "method" of "read"

@JsonRpc("read")
public RecordResponse readRecord(@Context Identity myIdentity, RecordRequest request)
{
    // ...
}
```

### Programmatic definition

```java
JsonRpcMethod method = new JsonRpcMethod(MyClass.class, MyClass.class.getMethod(...), "methodName", "POST");
```

## Errors

Rather than throw generic exceptions or error responses, use
`JsonRpcErrorDetail.exception(...)` to generate a JSON-RPC exception to throw.
This ensures that a proper JSON-RPC error is generated.

## Installing the JSON-RPC Guice module

```java
Builder<?> builder = JsonRpcModule.builder();

// default base path is "jsonrpc" - use this method to change it
// it can contain any number of /
builder.withBasePath("..")

// add all @JsonRpc methods in the specified class    
JsonRpcMethod.addAllInClass(builder, MyClass.class);

// add any programmatic instances, if any
builder.add(...);

Module module = builder.builder();
```