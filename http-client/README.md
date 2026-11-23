You can create an `HttpClient` using the `HttpClientBinder` and have it injected into your code using Guice. First, create an annotation for your client:

    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @Qualifier
    public @interface FooClient {}

Second, create an `HttpClient` binding for that annotation:

    httpClientBinder(binder).bindHttpClient("foo", FooClient.class);

Finally, use the annotation to have the `HttpClient` injected into your code:

    public class Foo
    {
        @Inject
        public Foo(@FooClient HttpClient httpClient)
        ...
    }

The reason to use different client bindings for each service is to allow them to be configured individually.  In the example above, the string `"foo"` passed to `bindHttpClient` specifies the prefix for the configuration. This means that you can configure the connect timeout via the config property named `foo.http-client.connect-timeout`. The config class is `HttpClientConfig`. See the config class for the full list of configurable properties.

The client supports modifying requests via a user specified set of filters. These filters are added using the binder:

    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
        .withFilter(FirstFilter.class)
        .withFilter(SecondFilter.class);

### Tracing

The Jetty HTTP client has built-in OpenTelemetry instrumentation; no tracing filter or
`withTracing()` binder call is needed. For a Guice-bound client, the HTTP client module
uses the `OpenTelemetry` and `Tracer` bindings when available. Without those bindings,
it defaults to no-op telemetry and a no-op tracer.

For an Airlift application, install `OpenTelemetryModule` and
`OpenTelemetryExporterModule` as described in the [OpenTelemetry README](../opentelemetry/README.md).
With an exporter configured, the client creates a CLIENT span for each request and uses
the configured propagator to inject its context into outgoing headers. Airlift's
OpenTelemetry module configures W3C trace context propagation. For custom OpenTelemetry
setups, supply a `Tracer` and an `OpenTelemetry` instance with the desired propagators.
The HTTP client also accepts an explicit `SpanBuilder` on a `Request` when a request
needs a custom client span.

The old `TraceTokenRequestFilter` and `.withTracing()` API were removed. Migrate by
removing the binder call and configuring OpenTelemetry in the application and downstream
services. W3C trace context is a different propagation format from the old
`X-Airlift-TraceToken` header; services that still require that header need a separate
migration strategy.

### Aliases

The binder also supports alias annotations. These are useful when you have multiple logical clients that all use the same physical service. For example, the `foo-server` might implement the `foo` and `bar` services. In the future, the `bar` service could be moved to it's own `bar-server`. Using aliases allows you to have separate annotations, `FooClient` and `BarClient`, for the different logical services, making it easy to later migrate to separate clients and configurations by making a single change to the bindings.

Using aliases is similar to using filters:

    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
        .withAlias(BarClient.class)
        .withAlias(QuxClient.class);

Note that the filters and aliases are unique to the binding, allowing you to have a different set for each one.

### Request logging

The HTTP client also supports logging, which can be enabled with the `http-client.log.enabled` config property.
Here is a sample log line:

```
2018-02-21T18:26:50.438-08:00	HTTP/1.1	DELETE	http://127.0.0.1:8080/v1/task/20180222_022649_00001_dn54g.0.0/results/0	204	0	16	1	2	3	4	16
```
A request log line has twelve columns delimited with the tab character:
- Request timestamp in the ISO 8601 format.
- The protocol version.
- The method (GET, POST, DELETE, etc.).
- The URI.
- Response status code if available, otherwise the failure reason (e.g., TIMEOUT).
- Response size in bytes.
- Total request time [ms].
- Request queue time [ms].
- Time from request processing begins to request is sent or failed to be sent [ms].
- Time from request is sent to response begins [ms].
- Time from response begins to response is complete [ms].
- Time to last byte [ms], which is the amount of time from when the request is created to when the entire response is received.
