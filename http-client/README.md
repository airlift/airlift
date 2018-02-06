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

When using the `HttpClient` with other platform services, you can use the `TraceTokenRequestFilter` to automatically send the current trace token with each request. The binder has shortcut for this:

    httpClientBinder(binder).bindHttpClient("foo", FooClient.class).withTracing();

The binder also supports alias annotations. These are useful when you have multiple logical clients that all use the same physical service. For example, the `foo-server` might implement the `foo` and `bar` services. In the future, the `bar` service could be moved to it's own `bar-server`. Using aliases allows you to have separate annotations, `FooClient` and `BarClient`, for the different logical services, making it easy to later migrate to separate clients and configurations by making a single change to the bindings.

Using aliases is similar to using filters:

    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
        .withAlias(BarClient.class)
        .withAlias(QuxClient.class);

Note that the filters and aliases are unique to the binding, allowing you to have a different set for each one.

The HTTP client also supports logging, which can be enabled with the `http-client.log.enabled` config property.
Here is a sample log line:

```
2018-02-21T18:26:50.438-08:00	HTTP/1.1	DELETE	http://127.0.0.1:8080/v1/task/20180222_022649_00001_dn54g.0.0/results/0	204	0	16	null
```
A request log line has eight columns delimited with the tab character:
- Request timestamp in the ISO 8601 format.
- The protocol version.
- The method (GET, POST, DELETE, etc.).
- The URI.
- Response status code if available, otherwise the failure reason (e.g., TIMEOUT).
- Response size in bytes.
- Airlift trace token if present in the request headers ("X-Airlift-TraceToken").
- Time to last byte, which is the amount of time from when the request is created to when the entire response is received.
