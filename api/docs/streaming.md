[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Streaming Responses

`@ApiGet` and `@ApiCreate` methods can stream responses by returning an `ApiTextStreamResponse`, `ApiByteStreamResponse`, or `ApiOutputStreamResponse` instance. The instance must be
parameterized with a [resource](resources.md) that it represents. `@ApiCustom` methods can also stream responses when their `type` is `GET` or `CREATE`.

E.g.

```java
@ApiGet(...)
public ApiTextStreamResponse<MyResource> myStreamedResponse(...)
{
    return new ApiTextStreamResponse<>(myTextStream);
}

@ApiGet(...)
public ApiByteStreamResponse<MyResource> myStreamedResponse(...)
{
    return new ApiByteStreamResponse<>(myByteStream);
}

@ApiGet(...)
public ApiOutputStreamResponse<MyResource> myStreamedResponse(...)
{
    Consumer<OutputStream> consumer = outputStream -> {
        // write to the output stream
    };
    return new ApiOutputStreamResponse<>(consumer);
}

@ApiCreate(...)
public ApiTextStreamResponse<MyResource> myPostStreamedResponse(MyCreateRequest request)
{
    return new ApiTextStreamResponse<>(myTextStream);
}

@ApiCustom(type = CREATE, verb = "stream", ...)
public ApiByteStreamResponse<MyResource> myPostCustomStreamedResponse(MyCreateRequest request)
{
    return new ApiByteStreamResponse<>(myByteStream);
}
```
