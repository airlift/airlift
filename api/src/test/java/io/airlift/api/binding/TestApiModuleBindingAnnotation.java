package io.airlift.api.binding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDeprecated;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiIdLookup;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiRequestFilter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResponseFilter;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.ApiStringId;
import io.airlift.api.ApiType;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiResource;
import io.airlift.api.servertests.openapi.DummyService;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public class TestApiModuleBindingAnnotation
{
    @Test
    public void testAnnotatedApiModuleBindsResourcesToQualifiedJaxrsInstance()
    {
        Injector injector = new Bootstrap(
                ApiModule.builder()
                        .addApi(apiBuilder -> apiBuilder.add(DummyService.class))
                        .build(),
                ApiModule.builder()
                        .annotatedWith(Internal.class)
                        .addApi(apiBuilder -> apiBuilder.add(DummyService.class))
                        .build(),
                new JaxrsModule(),
                new JaxrsModule(Internal.class),
                new JsonModule())
                .quiet()
                .initialize();

        try {
            assertThat(apiMethods(injector.getInstance(ResourceConfig.class))).hasSize(1);
            assertThat(apiMethods(injector.getInstance(Key.get(ResourceConfig.class, Internal.class)))).hasSize(1);
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    @Test
    public void testAnnotatedApiModuleDoesNotBindResourcesToDefaultJaxrsInstance()
    {
        Injector injector = new Bootstrap(
                ApiModule.builder()
                        .annotatedWith(Internal.class)
                        .addApi(apiBuilder -> apiBuilder.add(DummyService.class))
                        .build(),
                new JaxrsModule(),
                new JaxrsModule(Internal.class),
                new JsonModule())
                .quiet()
                .initialize();

        try {
            assertThat(apiMethods(injector.getInstance(ResourceConfig.class))).isEmpty();
            assertThat(apiMethods(injector.getInstance(Key.get(ResourceConfig.class, Internal.class)))).hasSize(1);
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    @Test
    public void testOpenApiResourceUsesApiModuleBindingAnnotation()
    {
        Injector injector = new Bootstrap(
                ApiModule.builder()
                        .annotatedWith(Internal.class)
                        .addApi(apiBuilder -> apiBuilder.add(DummyService.class))
                        .withOpenApiMetadata(openApiMetadata("/"))
                        .build(),
                new JaxrsModule(),
                new JaxrsModule(Internal.class),
                new JsonModule())
                .quiet()
                .initialize();

        try {
            assertThat(methodsFor(injector.getInstance(ResourceConfig.class), OpenApiResource.class)).isEmpty();
            assertThat(methodsFor(injector.getInstance(Key.get(ResourceConfig.class, Internal.class)), OpenApiResource.class)).hasSize(2);
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    @Test
    public void testTwoAnnotatedHttpServersHaveIndependentApiAndOpenApiResources()
            throws Exception
    {
        assertIndependentApiAndOpenApiResources(ALPHA_API_SERVER, BETA_API_SERVER);
    }

    @Test
    public void testDefaultAndAnnotatedHttpServersHaveIndependentApiAndOpenApiResources()
            throws Exception
    {
        assertIndependentApiAndOpenApiResources(DEFAULT_API_SERVER, GAMMA_API_SERVER);
    }

    @Test
    public void testTwoAnnotatedHttpServersKeepQualifiedRegistrationsIndependent()
            throws Exception
    {
        assertQualifiedRegistrationsIndependent(ALPHA_ADVANCED_SERVER, BETA_ADVANCED_SERVER);
    }

    @Test
    public void testDefaultAndAnnotatedHttpServersKeepQualifiedRegistrationsIndependent()
            throws Exception
    {
        assertQualifiedRegistrationsIndependent(DEFAULT_ADVANCED_SERVER, GAMMA_ADVANCED_SERVER);
    }

    @Test
    public void testSameApiServiceClassCanBeRegisteredInMultipleAnnotatedServers()
            throws Exception
    {
        assertSharedServiceServers(ALPHA_SHARED_SERVER, BETA_SHARED_SERVER);
    }

    private static List<ResourceMethod> apiMethods(ResourceConfig resourceConfig)
    {
        return methodsFor(resourceConfig, DummyService.class);
    }

    private static List<ResourceMethod> methodsFor(ResourceConfig resourceConfig, Class<?> handlerClass)
    {
        return allMethods(resourceConfig.getResources()).stream()
                .filter(method -> method.getInvocable().getHandler().getHandlerClass().equals(handlerClass))
                .collect(toImmutableList());
    }

    private static List<ResourceMethod> allMethods(Iterable<Resource> resources)
    {
        List<ResourceMethod> methods = new ArrayList<>();
        for (Resource resource : resources) {
            methods.addAll(resource.getAllMethods());
            methods.addAll(allMethods(resource.getChildResources()));
        }
        return methods;
    }

    private static OpenApiMetadata openApiMetadata(String basePath)
    {
        return new OpenApiMetadata(Optional.empty(), ImmutableList.of(), basePath, Duration.ofMinutes(5), OPENAPI_3_0_1);
    }

    private static final ServerBinding DEFAULT_SERVER = new ServerBinding(Optional.empty(), "", "");
    private static final ServerBinding ALPHA_SERVER = new ServerBinding(Optional.of(AlphaServer.class), "alpha-server", "alpha");
    private static final ServerBinding BETA_SERVER = new ServerBinding(Optional.of(BetaServer.class), "beta-server", "beta");
    private static final ServerBinding GAMMA_SERVER = new ServerBinding(Optional.of(GammaServer.class), "gamma-server", "gamma");

    private static final SimpleApiServer ALPHA_API_SERVER = new SimpleApiServer(ALPHA_SERVER, AlphaService.class, "alpha");
    private static final SimpleApiServer BETA_API_SERVER = new SimpleApiServer(BETA_SERVER, BetaService.class, "beta");
    private static final SimpleApiServer DEFAULT_API_SERVER = new SimpleApiServer(DEFAULT_SERVER, DefaultService.class, "defaulted");
    private static final SimpleApiServer GAMMA_API_SERVER = new SimpleApiServer(GAMMA_SERVER, GammaService.class, "gamma");

    private static final AdvancedApiServer ALPHA_ADVANCED_SERVER = new AdvancedApiServer(ALPHA_SERVER, AlphaAdvancedService.class, "alphaAdvanced", "/alpha-docs", "alpha-", "alpha-extension", "AlphaOnlyAdvanced", true, true);
    private static final AdvancedApiServer BETA_ADVANCED_SERVER = new AdvancedApiServer(BETA_SERVER, BetaAdvancedService.class, "betaAdvanced", "/beta-docs", "beta-", "beta-extension", "BetaOnlyAdvanced", false, false);
    private static final AdvancedApiServer DEFAULT_ADVANCED_SERVER = new AdvancedApiServer(DEFAULT_SERVER, DefaultAdvancedService.class, "defaultAdvanced", "/default-docs", "default-", "default-extension", "DefaultOnlyAdvanced", true, false);
    private static final AdvancedApiServer GAMMA_ADVANCED_SERVER = new AdvancedApiServer(GAMMA_SERVER, GammaAdvancedService.class, "gammaAdvanced", "/gamma-docs", "gamma-", "gamma-extension", "GammaOnlyAdvanced", false, true);

    private static final SharedServiceServer ALPHA_SHARED_SERVER = new SharedServiceServer(ALPHA_SERVER, "/alpha-shared-docs", "alpha-shared-extension");
    private static final SharedServiceServer BETA_SHARED_SERVER = new SharedServiceServer(BETA_SERVER, "/beta-shared-docs", "beta-shared-extension");

    private static ApiIdLookup<SharedLookupId> lookupWithPrefix(String prefix)
    {
        return (_, requestValue) -> Optional.of(new SharedLookupId(prefix + requestValue));
    }

    private static ServerContext createServerContext(ApiServer... servers)
    {
        ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
        ImmutableList.Builder<Module> serverModules = ImmutableList.builder();
        for (ApiServer server : servers) {
            server.serverBinding().addProperties(properties);
            server.serverBinding().addServerModules(serverModules);
            serverModules.add(server.apiModule());
        }

        Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        ImmutableList.Builder<Module> moduleList = ImmutableList.<Module>builder()
                .add(new TestingNodeModule())
                .add(new JsonModule())
                .add(TestApiModuleBindingAnnotation::bindNoopOpenTelemetry)
                .add(binder -> binder.bind(new TypeLiteral<Map<String, AtomicInteger>>() {}).toInstance(counters))
                .addAll(serverModules.build());

        Injector injector = new Bootstrap(moduleList.build())
                .setRequiredConfigurationProperties(properties.buildOrThrow())
                .quiet()
                .doNotInitializeLogging()
                .initialize();
        return new ServerContext(injector, counters);
    }

    private static void assertIndependentApiAndOpenApiResources(SimpleApiServer first, SimpleApiServer second)
            throws Exception
    {
        try (ServerContext context = createServerContext(first, second)) {
            assertSimpleApiServer(context, first, second);
            assertSimpleApiServer(context, second, first);
        }
    }

    private static void assertSimpleApiServer(ServerContext context, SimpleApiServer server, SimpleApiServer other)
    {
        URI baseUri = server.baseUri(context);

        assertOk(context, baseUri, server.apiPath());
        assertNotFound(context, baseUri, other.apiPath());
        assertOpenApiContainsOnly(context, baseUri, server.serviceType(), "/" + server.apiPath(), "/" + other.apiPath());
        assertNotFound(context, baseUri, other.openApiPath());
    }

    private static void assertQualifiedRegistrationsIndependent(AdvancedApiServer first, AdvancedApiServer second)
            throws Exception
    {
        try (ServerContext context = createServerContext(first, second)) {
            assertAdvancedServer(context, first, second);
            assertAdvancedServer(context, second, first);

            assertNotFound(context, first.baseUri(context), second.openApiPath());
            assertNotFound(context, second.baseUri(context), first.openApiPath());
        }
    }

    private static void assertSharedServiceServers(SharedServiceServer first, SharedServiceServer second)
            throws Exception
    {
        try (ServerContext context = createServerContext(first, second)) {
            assertSharedServiceServer(context, first, second);
            assertSharedServiceServer(context, second, first);
        }
    }

    private static void assertAdvancedServer(ServerContext context, AdvancedApiServer server, AdvancedApiServer other)
            throws IOException
    {
        assertAdvancedServer(
                context,
                server.baseUri(context),
                server.serviceType(),
                server.openApiBasePath(),
                server.lookupPrefix(),
                server.expectedSchema(),
                other.expectedSchema(),
                server.extensionValue(),
                server.expectDeprecatedOperation(),
                server.hideOpenApiFilteredMethod());
    }

    private static void assertAdvancedServer(
            ServerContext context,
            URI baseUri,
            String serviceType,
            String openApiBasePath,
            String lookupPrefix,
            String expectedSchema,
            String unexpectedSchema,
            String extensionValue,
            boolean expectDeprecatedOperation,
            boolean expectOpenApiFilteredMethodHidden)
            throws IOException
    {
        String openApi = getOpenApi(context, baseUri, openApiBasePath, serviceType);
        assertThat(schemaNames(openApi))
                .contains(expectedSchema, "SharedAdvanced", "LookupAdvanced")
                .doesNotContain(unexpectedSchema);
        assertThat(openApi)
                .contains("/%s/api/v1/%s".formatted(serviceType, decapitalize(expectedSchema)))
                .contains("%s/%s/openapi/v1/json".formatted(openApiBasePath, serviceType))
                .contains("This parameter can be looked up using `name`")
                .contains(extensionValue)
                .doesNotContain(unexpectedSchema);
        assertThat(operationForVerb(openApi, "deprecated").path("deprecated").asBoolean(false)).isEqualTo(expectDeprecatedOperation);
        assertThat(hasPathWithVerb(openApi, "openApiFiltered")).isEqualTo(!expectOpenApiFilteredMethodHidden);

        assertOk(context, baseUri, "%s/api/v1/sharedAdvanced:openApiFiltered".formatted(serviceType));

        String filterPath = pathWithVerb(openApi, "filter");
        assertOk(context, baseUri, filterPath);
        assertThat(context.counter("request:%s".formatted(serviceType))).isEqualTo(1);
        assertThat(context.counter("response:%s".formatted(serviceType))).isEqualTo(1);

        String builderFilterPath = pathWithVerb(openApi, "builderFilter");
        assertOk(context, baseUri, builderFilterPath);
        assertThat(context.counter("builder-request:%s".formatted(serviceType))).isEqualTo(1);
        assertThat(context.counter("builder-response:%s".formatted(serviceType))).isEqualTo(1);

        String lookupPath = pathWithVerb(openApi, "lookup").replaceAll("\\{[^}]+}", "name=value");
        StringResponse lookupResponse = context.client.execute(prepareGet().setUri(uri(baseUri, lookupPath)).build(), createStringResponseHandler());
        assertThat(lookupResponse.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(lookupResponse.getBody()).contains(lookupPrefix + "value");

        assertThat(statusPost(context, baseUri, "%s/api/v1/:quota".formatted(serviceType)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT.code());
        assertThat(statusPost(context, baseUri, "%s/api/v1/:missingQuota".formatted(serviceType)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.code());
    }

    private static void assertSharedServiceServer(ServerContext context, SharedServiceServer server, SharedServiceServer other)
    {
        assertOk(context, server.baseUri(context), server.apiPath());

        String openApi = getOpenApi(context, server.baseUri(context), server.openApiBasePath(), server.serviceType());
        assertThat(openApi)
                .contains("/" + server.apiPath())
                .contains(server.extensionValue())
                .doesNotContain(other.extensionValue());
    }

    private static Set<String> schemaNames(String openApi)
            throws IOException
    {
        Set<String> schemas = new LinkedHashSet<>();
        Iterator<String> names = JsonMapper.builder().build().readTree(openApi).get("components").get("schemas").fieldNames();
        while (names.hasNext()) {
            schemas.add(names.next());
        }
        return schemas;
    }

    private static String getOpenApi(ServerContext context, URI baseUri, String openApiBasePath, String serviceType)
    {
        StringResponse response = context.client.execute(prepareGet()
                .setUri(uri(baseUri, "%s/%s/openapi/v1/json".formatted(openApiBasePath, serviceType)))
                .build(), createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        return response.getBody();
    }

    private static String pathWithVerb(String openApi, String verb)
            throws IOException
    {
        JsonNode paths = JsonMapper.builder().build().readTree(openApi).get("paths");
        Iterator<String> names = paths.fieldNames();
        while (names.hasNext()) {
            String path = names.next();
            if (path.contains(":" + verb)) {
                return path;
            }
        }
        throw new AssertionError("No OpenAPI path found for verb: " + verb);
    }

    private static boolean hasPathWithVerb(String openApi, String verb)
            throws IOException
    {
        JsonNode paths = JsonMapper.builder().build().readTree(openApi).get("paths");
        Iterator<String> names = paths.fieldNames();
        while (names.hasNext()) {
            if (names.next().contains(":" + verb)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode operationForVerb(String openApi, String verb)
            throws IOException
    {
        JsonNode paths = JsonMapper.builder().build().readTree(openApi).get("paths");
        Iterator<String> names = paths.fieldNames();
        while (names.hasNext()) {
            String path = names.next();
            if (path.contains(":" + verb)) {
                return paths.get(path).get("get");
            }
        }
        throw new AssertionError("No OpenAPI operation found for verb: " + verb);
    }

    private static String decapitalize(String value)
    {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void bindNoopOpenTelemetry(Binder binder)
    {
        try {
            Class openTelemetryClass = Class.forName("io.opentelemetry.api.OpenTelemetry");
            Object openTelemetry = openTelemetryClass.getMethod("noop").invoke(null);
            Class tracerClass = Class.forName("io.opentelemetry.api.trace.Tracer");
            Object tracer = openTelemetryClass.getMethod("getTracer", String.class).invoke(openTelemetry, "api-binding-test");

            binder.bind(openTelemetryClass).toInstance(openTelemetry);
            binder.bind(tracerClass).toInstance(tracer);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertOk(ServerContext context, URI baseUri, String path)
    {
        assertThat(status(context, baseUri, path).getStatusCode()).isEqualTo(HttpStatus.OK.code());
    }

    private static void assertNotFound(ServerContext context, URI baseUri, String path)
    {
        assertThat(status(context, baseUri, path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.code());
    }

    private static StatusResponse status(ServerContext context, URI baseUri, String path)
    {
        return context.client.execute(prepareGet().setUri(uri(baseUri, path)).build(), createStatusResponseHandler());
    }

    private static StatusResponse statusPost(ServerContext context, URI baseUri, String path)
    {
        return context.client.execute(preparePost().setUri(uri(baseUri, path)).build(), createStatusResponseHandler());
    }

    private static void assertOpenApiContainsOnly(ServerContext context, URI baseUri, String serviceType, String expectedPath, String unexpectedPath)
    {
        StringResponse response = context.client.execute(Request.Builder.prepareGet()
                .setUri(uri(baseUri, "%s/openapi/v1/json".formatted(serviceType)))
                .build(), createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(response.getBody())
                .contains(expectedPath)
                .doesNotContain(unexpectedPath);
    }

    private static URI uri(URI baseUri, String path)
    {
        return UriBuilder.fromUri(baseUri).path(path).build();
    }

    private interface ApiServer
    {
        ServerBinding serverBinding();

        Module apiModule();
    }

    private record ServerBinding(Optional<Class<? extends Annotation>> bindingAnnotation, String serverName, String configPrefix)
    {
        private void addProperties(ImmutableMap.Builder<String, String> properties)
        {
            properties.put(propertyPrefix() + "http-server.http.port", "0");
            properties.put(propertyPrefix() + "http-server.log.enabled", "false");
        }

        private void addServerModules(ImmutableList.Builder<Module> modules)
        {
            if (bindingAnnotation.isEmpty()) {
                modules.add(new HttpServerModule());
                modules.add(new JaxrsModule());
                return;
            }
            Class<? extends Annotation> annotation = bindingAnnotation.orElseThrow();
            modules.add(new HttpServerModule(serverName, annotation, configPrefix));
            modules.add(new JaxrsModule(annotation));
        }

        private URI baseUri(ServerContext context)
        {
            if (bindingAnnotation.isEmpty()) {
                return context.baseUri();
            }
            return context.baseUri(bindingAnnotation.orElseThrow());
        }

        private void configure(ApiModule.Builder builder)
        {
            bindingAnnotation.ifPresent(builder::annotatedWith);
        }

        private String propertyPrefix()
        {
            return bindingAnnotation.isEmpty() ? "" : configPrefix + ".";
        }
    }

    private record SimpleApiServer(ServerBinding serverBinding, Class<?> serviceClass, String serviceType)
            implements ApiServer
    {
        @Override
        public Module apiModule()
        {
            ApiModule.Builder builder = ApiModule.builder()
                    .addApi(apiBuilder -> apiBuilder.add(serviceClass))
                    .withOpenApiMetadata(openApiMetadata("/"));
            serverBinding.configure(builder);
            return builder.build();
        }

        private URI baseUri(ServerContext context)
        {
            return serverBinding.baseUri(context);
        }

        private String apiPath()
        {
            return serviceType + "/api/v1/" + serviceType;
        }

        private String openApiPath()
        {
            return serviceType + "/openapi/v1/json";
        }
    }

    private record AdvancedApiServer(
            ServerBinding serverBinding,
            Class<?> serviceClass,
            String serviceType,
            String openApiBasePath,
            String lookupPrefix,
            String extensionValue,
            String expectedSchema,
            boolean expectDeprecatedOperation,
            boolean hideOpenApiFilteredMethod)
            implements ApiServer
    {
        @Override
        public Module apiModule()
        {
            ApiModule.Builder builder = ApiModule.builder()
                    .addApi(apiBuilder -> apiBuilder.add(serviceClass))
                    .addRequestFilter(method -> method.method().getName().equals("getBuilderFiltered"), SharedBuilderRequestFilter.class)
                    .addResponseFilter(method -> method.method().getName().equals("getBuilderFiltered"), SharedBuilderResponseFilter.class)
                    .addIdLookupBinding(SharedLookupId.class, binding -> binding.toInstance(lookupWithPrefix(lookupPrefix)))
                    .withOpenApiMetadata(openApiMetadata(openApiBasePath))
                    .withOpenApiFilterBinding(binding -> binding.toInstance(_ -> method -> !hideOpenApiFilteredMethod || !method.getName().equals("getOpenApiFiltered")))
                    .withOpenApiExtensionFilter((_, _, operation) -> {
                        operation.addExtension("x-binding-test", extensionValue);
                        return operation;
                    });
            serverBinding.configure(builder);
            return builder.build();
        }

        private URI baseUri(ServerContext context)
        {
            return serverBinding.baseUri(context);
        }

        private String openApiPath()
        {
            return "%s/%s/openapi/v1/json".formatted(openApiBasePath, serviceType);
        }
    }

    private record SharedServiceServer(ServerBinding serverBinding, String openApiBasePath, String extensionValue)
            implements ApiServer
    {
        @Override
        public Module apiModule()
        {
            ApiModule.Builder builder = ApiModule.builder()
                    .addApi(apiBuilder -> apiBuilder.add(SharedServerService.class))
                    .withOpenApiMetadata(openApiMetadata(openApiBasePath))
                    .withOpenApiExtensionFilter((_, _, operation) -> {
                        operation.addExtension("x-binding-test", extensionValue);
                        return operation;
                    });
            serverBinding.configure(builder);
            return builder.build();
        }

        private URI baseUri(ServerContext context)
        {
            return serverBinding.baseUri(context);
        }

        private String serviceType()
        {
            return "sharedServer";
        }

        private String apiPath()
        {
            return serviceType() + "/api/v1/" + serviceType();
        }
    }

    private static class ServerContext
            implements AutoCloseable
    {
        private final Map<String, AtomicInteger> counters;
        private final Injector injector;
        private final JettyHttpClient client = new JettyHttpClient("api-binding-test", new HttpClientConfig());

        private ServerContext(Injector injector, Map<String, AtomicInteger> counters)
        {
            this.injector = injector;
            this.counters = counters;
        }

        private URI baseUri()
        {
            return injector.getInstance(HttpServerInfo.class).getHttpUri();
        }

        private URI baseUri(Class<? extends Annotation> annotation)
        {
            return injector.getInstance(Key.get(HttpServerInfo.class, annotation)).getHttpUri();
        }

        private int counter(String key)
        {
            return Optional.ofNullable(counters.get(key))
                    .map(AtomicInteger::get)
                    .orElse(0);
        }

        @Override
        public void close()
                throws Exception
        {
            try {
                client.close();
            }
            finally {
                injector.getInstance(LifeCycleManager.class).stop();
            }
        }
    }

    @BindingAnnotation
    @Retention(RUNTIME)
    private @interface Internal {}

    @BindingAnnotation
    @Retention(RUNTIME)
    private @interface AlphaServer {}

    @BindingAnnotation
    @Retention(RUNTIME)
    private @interface BetaServer {}

    @BindingAnnotation
    @Retention(RUNTIME)
    private @interface GammaServer {}

    @ApiService(type = AlphaType.class, name = "alpha", description = "Alpha service")
    public static class AlphaService
    {
        @ApiGet(description = "Get alpha")
        public AlphaResource get()
        {
            return new AlphaResource("alpha");
        }
    }

    @ApiService(type = BetaType.class, name = "beta", description = "Beta service")
    public static class BetaService
    {
        @ApiGet(description = "Get beta")
        public BetaResource get()
        {
            return new BetaResource("beta");
        }
    }

    @ApiService(type = DefaultType.class, name = "defaulted", description = "Default service")
    public static class DefaultService
    {
        @ApiGet(description = "Get default")
        public DefaultResource get()
        {
            return new DefaultResource("defaulted");
        }
    }

    @ApiService(type = GammaType.class, name = "gamma", description = "Gamma service")
    public static class GammaService
    {
        @ApiGet(description = "Get gamma")
        public GammaResource get()
        {
            return new GammaResource("gamma");
        }
    }

    @ApiResource(name = "alpha", description = "Alpha resource")
    public record AlphaResource(String value) {}

    @ApiResource(name = "beta", description = "Beta resource")
    public record BetaResource(String value) {}

    @ApiResource(name = "defaulted", description = "Default resource")
    public record DefaultResource(String value) {}

    @ApiResource(name = "gamma", description = "Gamma resource")
    public record GammaResource(String value) {}

    @ApiService(type = AlphaAdvancedType.class, name = "alpha advanced", description = "Alpha advanced service")
    public static class AlphaAdvancedService
    {
        private final ApiQuotaController quotaController;

        @Inject
        public AlphaAdvancedService(@AlphaServer ApiQuotaController quotaController)
        {
            this.quotaController = quotaController;
        }

        @ApiGet(description = "Get alpha advanced")
        public AlphaOnlyAdvancedResource getAlpha()
        {
            return new AlphaOnlyAdvancedResource("alpha");
        }

        @ApiCustom(type = ApiType.GET, verb = "filter", description = "Get shared filtered alpha advanced")
        @ApiRequestFilter(SharedRequestFilter.class)
        @ApiResponseFilter(SharedResponseFilter.class)
        public SharedAdvancedResource getFiltered()
        {
            return new SharedAdvancedResource("alpha");
        }

        @ApiCustom(type = ApiType.GET, verb = "builderFilter", description = "Get shared builder-filtered alpha advanced")
        public SharedAdvancedResource getBuilderFiltered()
        {
            return new SharedAdvancedResource("alpha");
        }

        @ApiCustom(type = ApiType.GET, verb = "openApiFiltered", description = "Get OpenAPI-filtered alpha advanced")
        public SharedAdvancedResource getOpenApiFiltered()
        {
            return new SharedAdvancedResource("alpha");
        }

        @ApiDeprecated(information = "Alpha advanced deprecated")
        @ApiCustom(type = ApiType.GET, verb = "deprecated", description = "Get deprecated alpha advanced")
        public SharedAdvancedResource getDeprecated()
        {
            return new SharedAdvancedResource("alpha");
        }

        @ApiCustom(type = ApiType.GET, verb = "lookup", description = "Lookup alpha advanced")
        public LookupAdvancedResource lookup(@ApiParameter SharedLookupId lookupAdvancedId)
        {
            return new LookupAdvancedResource(lookupAdvancedId.toString());
        }

        @ApiCustom(type = ApiType.CREATE, verb = "quota", description = "Record alpha quota", quotas = "alphaQuota")
        public void recordQuota(@Context jakarta.ws.rs.core.Request request)
        {
            quotaController.recordQuotaUsage(request, "alphaQuota");
        }

        @ApiCustom(type = ApiType.CREATE, verb = "missingQuota", description = "Miss alpha quota", quotas = "alphaQuota")
        public void missQuota() {}
    }

    @ApiService(type = BetaAdvancedType.class, name = "beta advanced", description = "Beta advanced service")
    public static class BetaAdvancedService
    {
        private final ApiQuotaController quotaController;

        @Inject
        public BetaAdvancedService(@BetaServer ApiQuotaController quotaController)
        {
            this.quotaController = quotaController;
        }

        @ApiGet(description = "Get beta advanced")
        public BetaOnlyAdvancedResource getBeta()
        {
            return new BetaOnlyAdvancedResource("beta");
        }

        @ApiCustom(type = ApiType.GET, verb = "filter", description = "Get shared filtered beta advanced")
        @ApiRequestFilter(SharedRequestFilter.class)
        @ApiResponseFilter(SharedResponseFilter.class)
        public SharedAdvancedResource getFiltered()
        {
            return new SharedAdvancedResource("beta");
        }

        @ApiCustom(type = ApiType.GET, verb = "builderFilter", description = "Get shared builder-filtered beta advanced")
        public SharedAdvancedResource getBuilderFiltered()
        {
            return new SharedAdvancedResource("beta");
        }

        @ApiCustom(type = ApiType.GET, verb = "openApiFiltered", description = "Get OpenAPI-filtered beta advanced")
        public SharedAdvancedResource getOpenApiFiltered()
        {
            return new SharedAdvancedResource("beta");
        }

        @ApiCustom(type = ApiType.GET, verb = "deprecated", description = "Get beta advanced deprecated path")
        public SharedAdvancedResource getDeprecated()
        {
            return new SharedAdvancedResource("beta");
        }

        @ApiCustom(type = ApiType.GET, verb = "lookup", description = "Lookup beta advanced")
        public LookupAdvancedResource lookup(@ApiParameter SharedLookupId lookupAdvancedId)
        {
            return new LookupAdvancedResource(lookupAdvancedId.toString());
        }

        @ApiCustom(type = ApiType.CREATE, verb = "quota", description = "Record beta quota", quotas = "betaQuota")
        public void recordQuota(@Context jakarta.ws.rs.core.Request request)
        {
            quotaController.recordQuotaUsage(request, "betaQuota");
        }

        @ApiCustom(type = ApiType.CREATE, verb = "missingQuota", description = "Miss beta quota", quotas = "betaQuota")
        public void missQuota() {}
    }

    @ApiService(type = DefaultAdvancedType.class, name = "default advanced", description = "Default advanced service")
    public static class DefaultAdvancedService
    {
        private final ApiQuotaController quotaController;

        @Inject
        public DefaultAdvancedService(ApiQuotaController quotaController)
        {
            this.quotaController = quotaController;
        }

        @ApiGet(description = "Get default advanced")
        public DefaultOnlyAdvancedResource getDefault()
        {
            return new DefaultOnlyAdvancedResource("defaulted");
        }

        @ApiCustom(type = ApiType.GET, verb = "filter", description = "Get shared filtered default advanced")
        @ApiRequestFilter(SharedRequestFilter.class)
        @ApiResponseFilter(SharedResponseFilter.class)
        public SharedAdvancedResource getFiltered()
        {
            return new SharedAdvancedResource("defaulted");
        }

        @ApiCustom(type = ApiType.GET, verb = "builderFilter", description = "Get shared builder-filtered default advanced")
        public SharedAdvancedResource getBuilderFiltered()
        {
            return new SharedAdvancedResource("defaulted");
        }

        @ApiCustom(type = ApiType.GET, verb = "openApiFiltered", description = "Get OpenAPI-filtered default advanced")
        public SharedAdvancedResource getOpenApiFiltered()
        {
            return new SharedAdvancedResource("defaulted");
        }

        @ApiDeprecated(information = "Default advanced deprecated")
        @ApiCustom(type = ApiType.GET, verb = "deprecated", description = "Get deprecated default advanced")
        public SharedAdvancedResource getDeprecated()
        {
            return new SharedAdvancedResource("defaulted");
        }

        @ApiCustom(type = ApiType.GET, verb = "lookup", description = "Lookup default advanced")
        public LookupAdvancedResource lookup(@ApiParameter SharedLookupId lookupAdvancedId)
        {
            return new LookupAdvancedResource(lookupAdvancedId.toString());
        }

        @ApiCustom(type = ApiType.CREATE, verb = "quota", description = "Record default quota", quotas = "defaultQuota")
        public void recordQuota(@Context jakarta.ws.rs.core.Request request)
        {
            quotaController.recordQuotaUsage(request, "defaultQuota");
        }

        @ApiCustom(type = ApiType.CREATE, verb = "missingQuota", description = "Miss default quota", quotas = "defaultQuota")
        public void missQuota() {}
    }

    @ApiService(type = GammaAdvancedType.class, name = "gamma advanced", description = "Gamma advanced service")
    public static class GammaAdvancedService
    {
        private final ApiQuotaController quotaController;

        @Inject
        public GammaAdvancedService(@GammaServer ApiQuotaController quotaController)
        {
            this.quotaController = quotaController;
        }

        @ApiGet(description = "Get gamma advanced")
        public GammaOnlyAdvancedResource getGamma()
        {
            return new GammaOnlyAdvancedResource("gamma");
        }

        @ApiCustom(type = ApiType.GET, verb = "filter", description = "Get shared filtered gamma advanced")
        @ApiRequestFilter(SharedRequestFilter.class)
        @ApiResponseFilter(SharedResponseFilter.class)
        public SharedAdvancedResource getFiltered()
        {
            return new SharedAdvancedResource("gamma");
        }

        @ApiCustom(type = ApiType.GET, verb = "builderFilter", description = "Get shared builder-filtered gamma advanced")
        public SharedAdvancedResource getBuilderFiltered()
        {
            return new SharedAdvancedResource("gamma");
        }

        @ApiCustom(type = ApiType.GET, verb = "openApiFiltered", description = "Get OpenAPI-filtered gamma advanced")
        public SharedAdvancedResource getOpenApiFiltered()
        {
            return new SharedAdvancedResource("gamma");
        }

        @ApiCustom(type = ApiType.GET, verb = "deprecated", description = "Get gamma advanced deprecated path")
        public SharedAdvancedResource getDeprecated()
        {
            return new SharedAdvancedResource("gamma");
        }

        @ApiCustom(type = ApiType.GET, verb = "lookup", description = "Lookup gamma advanced")
        public LookupAdvancedResource lookup(@ApiParameter SharedLookupId lookupAdvancedId)
        {
            return new LookupAdvancedResource(lookupAdvancedId.toString());
        }

        @ApiCustom(type = ApiType.CREATE, verb = "quota", description = "Record gamma quota", quotas = "gammaQuota")
        public void recordQuota(@Context jakarta.ws.rs.core.Request request)
        {
            quotaController.recordQuotaUsage(request, "gammaQuota");
        }

        @ApiCustom(type = ApiType.CREATE, verb = "missingQuota", description = "Miss gamma quota", quotas = "gammaQuota")
        public void missQuota() {}
    }

    @ApiService(type = SharedServerType.class, name = "shared server", description = "Shared server service")
    public static class SharedServerService
    {
        @ApiGet(description = "Get shared server")
        public SharedServerResource get()
        {
            return new SharedServerResource("shared");
        }
    }

    @ApiResource(name = "alphaOnlyAdvanced", description = "Alpha-only advanced resource")
    public record AlphaOnlyAdvancedResource(String value) {}

    @ApiResource(name = "betaOnlyAdvanced", description = "Beta-only advanced resource")
    public record BetaOnlyAdvancedResource(String value) {}

    @ApiResource(name = "defaultOnlyAdvanced", description = "Default-only advanced resource")
    public record DefaultOnlyAdvancedResource(String value) {}

    @ApiResource(name = "gammaOnlyAdvanced", description = "Gamma-only advanced resource")
    public record GammaOnlyAdvancedResource(String value) {}

    @ApiResource(name = "sharedAdvanced", description = "Shared advanced resource")
    public record SharedAdvancedResource(String value) {}

    @ApiResource(name = "lookupAdvanced", description = "Lookup advanced resource")
    public record LookupAdvancedResource(String value) {}

    @ApiResource(name = "sharedServer", description = "Shared server resource")
    public record SharedServerResource(String value) {}

    @ApiIdSupportsLookup
    public static class SharedLookupId
            extends ApiStringId<LookupAdvancedResource>
    {
        public SharedLookupId()
        {
            super("default");
        }

        @JsonCreator
        public SharedLookupId(String id)
        {
            super(id);
        }
    }

    public static class SharedRequestFilter
            implements ContainerRequestFilter
    {
        private final Map<String, AtomicInteger> counters;

        @Inject
        public SharedRequestFilter(Map<String, AtomicInteger> counters)
        {
            this.counters = counters;
        }

        @Override
        public void filter(ContainerRequestContext requestContext)
        {
            counters.computeIfAbsent("request:" + firstPathSegment(requestContext), _ -> new AtomicInteger()).incrementAndGet();
        }
    }

    public static class SharedResponseFilter
            implements ContainerResponseFilter
    {
        private final Map<String, AtomicInteger> counters;

        @Inject
        public SharedResponseFilter(Map<String, AtomicInteger> counters)
        {
            this.counters = counters;
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
        {
            counters.computeIfAbsent("response:" + firstPathSegment(requestContext), _ -> new AtomicInteger()).incrementAndGet();
        }
    }

    public static class SharedBuilderRequestFilter
            implements ContainerRequestFilter
    {
        private final Map<String, AtomicInteger> counters;

        @Inject
        public SharedBuilderRequestFilter(Map<String, AtomicInteger> counters)
        {
            this.counters = counters;
        }

        @Override
        public void filter(ContainerRequestContext requestContext)
        {
            counters.computeIfAbsent("builder-request:" + firstPathSegment(requestContext), _ -> new AtomicInteger()).incrementAndGet();
        }
    }

    public static class SharedBuilderResponseFilter
            implements ContainerResponseFilter
    {
        private final Map<String, AtomicInteger> counters;

        @Inject
        public SharedBuilderResponseFilter(Map<String, AtomicInteger> counters)
        {
            this.counters = counters;
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
        {
            counters.computeIfAbsent("builder-response:" + firstPathSegment(requestContext), _ -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static String firstPathSegment(ContainerRequestContext requestContext)
    {
        return requestContext.getUriInfo().getPathSegments().getFirst().getPath();
    }

    public static class AlphaType
            extends TestServiceType
    {
        public AlphaType()
        {
            super("alpha");
        }
    }

    public static class BetaType
            extends TestServiceType
    {
        public BetaType()
        {
            super("beta");
        }
    }

    public static class DefaultType
            extends TestServiceType
    {
        public DefaultType()
        {
            super("defaulted");
        }
    }

    public static class GammaType
            extends TestServiceType
    {
        public GammaType()
        {
            super("gamma");
        }
    }

    public static class AlphaAdvancedType
            extends TestServiceType
    {
        public AlphaAdvancedType()
        {
            super("alphaAdvanced");
        }
    }

    public static class BetaAdvancedType
            extends TestServiceType
    {
        public BetaAdvancedType()
        {
            super("betaAdvanced");
        }
    }

    public static class DefaultAdvancedType
            extends TestServiceType
    {
        public DefaultAdvancedType()
        {
            super("defaultAdvanced");
        }
    }

    public static class GammaAdvancedType
            extends TestServiceType
    {
        public GammaAdvancedType()
        {
            super("gammaAdvanced");
        }
    }

    public static class SharedServerType
            extends TestServiceType
    {
        public SharedServerType()
        {
            super("sharedServer");
        }
    }

    private abstract static class TestServiceType
            implements ApiServiceType
    {
        private final String id;

        private TestServiceType(String id)
        {
            this.id = id;
        }

        @Override
        public String id()
        {
            return id;
        }

        @Override
        public int version()
        {
            return 1;
        }

        @Override
        public String title()
        {
            return id;
        }

        @Override
        public String description()
        {
            return id;
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return Set.of();
        }
    }
}
