[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Streaming Responses

`@ApiGet` and `@ApiCreate` methods can stream responses by returning an `ApiTextStreamResponse`, `ApiByteStreamResponse`, `ApiOutputStreamResponse`, or
`ApiServerSentEventStreamResponse` instance. The instance must be parameterized with a [resource](resources.md) that it represents. `@ApiCustom` methods can
also stream responses when their `type` is `GET` or `CREATE`.

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

@ApiGet(...)
public ApiServerSentEventStreamResponse<MyResource, MyEvent> myStreamedResponse(...)
{
    Consumer<OutputStream> consumer = outputStream -> {
        // write server-sent event frames such as: data: <json>\n\n
    };
    return new ApiServerSentEventStreamResponse<>(consumer);
}

@ApiCreate(...)
public ApiServerSentEventStreamResponse<MyResource, MyEvent> myPostStreamedResponse(MyCreateRequest request)
{
    Consumer<OutputStream> consumer = outputStream -> {
        // write server-sent event frames such as: data: <json>\n\n
    };
    return new ApiServerSentEventStreamResponse<>(consumer);
}
```

`ApiTextStreamResponse` produces `text/plain`, while `ApiByteStreamResponse` and `ApiOutputStreamResponse` produce `application/octet-stream`.

`ApiServerSentEventStreamResponse<RESOURCE, EVENT>` produces `text/event-stream`. `RESOURCE` keeps the API Builder resource and path semantics. `EVENT`
describes the JSON payload the application writes in each SSE `data:` frame. API Builder sets the response content type and documents the event payload type,
but the application still owns writing valid SSE frames.
