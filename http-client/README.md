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
