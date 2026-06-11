[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Request Cancellation

API methods can inject `@Context ApiCancellation` to register cleanup work that should run if
the request cannot complete normally, such as when the client disconnects, the request fails, or
the request times out.

```java
@ApiGet(description = "Get a report")
public Report getReport(@ApiParameter ReportId reportId, @Context ApiCancellation cancellation)
{
    cancellation.onCancel(cleanupExecutor, () -> report.cancel());
    return reportRunner.run(reportId);
}
```

Cancellation requires both API and HTTP server setup:

```java
ApiModule.builder()
        .addApi(ReportService.class)
        .withCancellableRequestExecutor(cancellableRequestExecutor)
        .build();

httpServerBinder(binder).withFeature(REQUEST_CANCELLATION);
```

The cancellable request executor runs API method bodies that declare `@Context ApiCancellation`,
so request work can be interrupted independently from the main HTTP request thread. Cancellation
listeners run on the executor supplied to `ApiCancellation.onCancel(...)`.

Size the cancellable request executor for the number of API requests using this feature that the
service must run concurrently. If the executor is saturated, requests whose API methods declare
`@Context ApiCancellation` wait for a thread from that pool, so the service backs up there instead
of on the main HTTP request thread.
