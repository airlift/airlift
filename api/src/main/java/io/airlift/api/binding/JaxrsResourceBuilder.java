package io.airlift.api.binding;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdLookup;
import io.airlift.api.ApiService;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiFilter;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.validation.ValidationContext;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.log.Logger;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.api.binding.ApiBindingKeys.annotatedKey;
import static io.airlift.api.binding.ApiMapBinders.newMapBinder;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_BINDING;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildFullPath;
import static io.airlift.api.internals.Mappers.buildMethodPath;
import static io.airlift.api.internals.Mappers.buildServicePath;
import static io.airlift.api.model.ModelResourceModifier.IS_MULTIPART_FORM;
import static io.airlift.api.model.ModelResourceModifier.IS_STREAMING_RESPONSE;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE;
import static java.util.Objects.requireNonNull;

class JaxrsResourceBuilder
{
    private static final Logger log = Logger.get(JaxrsResourceBuilder.class);
    private static final Set<ModelMethod> alreadyLogged = ConcurrentHashMap.newKeySet();

    private final Binder binder;
    private final ApiBindingProvider bindingProvider;
    private final JaxrsBinder jaxrsBinder;
    private final MapBinder<ModelService, Object> servicesBinder;
    private final Optional<Class<? extends Annotation>> bindingAnnotation;

    static JaxrsResourceBuilder jaxrsResourceBuilder(Binder binder, boolean withApiLogging, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return new JaxrsResourceBuilder(binder, withApiLogging, bindingAnnotation);
    }

    private JaxrsResourceBuilder(Binder binder, boolean withApiLogging, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
        jaxrsBinder = jaxrsBinder(binder, bindingAnnotation);
        this.binder = requireNonNull(binder, "binder is null");
        this.bindingProvider = new ApiBindingProvider(binder, bindingAnnotation);

        servicesBinder = newMapBinder(
                binder,
                new TypeLiteral<ModelService>() {},
                new TypeLiteral<Object>() {},
                bindingAnnotation)
                .permitDuplicates();

        jaxrsBinder.bind(JaxrsMethodFilter.class, jaxrsMethodFilterProvider());
        jaxrsBinder.bind(JaxrsQuotaFilter.class, jaxrsQuotaFilterProvider());
        if (withApiLogging) {
            jaxrsBinder.bind(JaxrsApiUsageLogFilter.class, jaxrsApiUsageLogFilterProvider());
        }
    }

    void bindFeatures()
    {
        newOptionalBinder(binder, annotatedKey(OpenApiProvider.class, bindingAnnotation));
        newOptionalBinder(binder, annotatedKey(OpenApiFilter.class, bindingAnnotation));
        newOptionalBinder(binder, annotatedKey(OpenApiMetadata.class, bindingAnnotation));

        bindAnnotated(PatchFieldsBuilder.class);
        jaxrsBinder.bind(SpecialApiTypeValueParamProvider.class, specialApiTypeValueParamProvider());
        jaxrsBinder.bind(PaginationValueParamProvider.class);
        jaxrsBinder.bind(JaxrsBindingBridge.class, jaxrsBindingBridgeProvider());
        jaxrsBinder.bind(JaxrsMapper.class, jaxrsMapperProvider());
        jaxrsBinder.bind(ApiStreamResponseWriter.class);
        jaxrsBinder.bind(JaxrsStatusValidator.class, jaxrsStatusValidatorProvider());
    }

    void bindService(ModelService service)
    {
        bindServiceClass(service);
        jaxrsBinder.bindInstance(buildService(service));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bindServiceClass(ModelService service)
    {
        if (bindingAnnotation.isEmpty()) {
            binder.bind(service.serviceClass()).in(Scopes.SINGLETON);
            servicesBinder.addBinding(service).to(service.serviceClass()).in(Scopes.SINGLETON);
            return;
        }

        Key serviceKey = annotatedKey(service.serviceClass(), bindingAnnotation);
        binder.bind(serviceKey).to(service.serviceClass()).in(Scopes.SINGLETON);
        servicesBinder.addBinding(service).to(serviceKey).in(Scopes.SINGLETON);
    }

    private Resource buildService(ModelService service)
    {
        Resource.Builder builder = Resource.builder();
        ApiService apiService = service.serviceClass().getDeclaredAnnotation(ApiService.class);
        builder.path(buildServicePath(ModelServiceMetadata.map(apiService)));
        for (ModelMethod method : service.methods()) {
            buildMethod(builder, service, method);
        }
        return builder.build();
    }

    private void buildMethod(Resource.Builder builder, ModelService service, ModelMethod method)
    {
        if (alreadyLogged.add(method)) {
            log.info("API %s %s %s#%s", method.httpMethod(), buildFullPath(service.service(), method, FOR_DISPLAY), service.serviceClass().getSimpleName(), method.method().getName());
        }

        Resource.Builder childBuilder = builder.addChildResource(buildMethodPath(method, FOR_BINDING));
        ResourceMethod.Builder methodBuilder = childBuilder.addMethod(method.httpMethod());
        switch (method.methodType()) {
            case GET -> methodBuilder.produces(getResponseType(method.returnType()));
            case LIST, DELETE -> methodBuilder.produces(APPLICATION_JSON_TYPE);
            case CREATE, UPDATE -> {
                boolean isMultiPartForm = method.requestBody().map(requestBody -> requestBody.modifiers().contains(IS_MULTIPART_FORM)).orElse(false);
                methodBuilder.consumes(isMultiPartForm ? MULTIPART_FORM_DATA_TYPE : APPLICATION_JSON_TYPE).produces(getResponseType(method.returnType()));
            }
        }
        methodBuilder.handledBy(service.serviceClass(), method.method());
    }

    private List<MediaType> getResponseType(ModelResource returnType)
    {
        if (returnType.modifiers().contains(IS_STREAMING_RESPONSE)) {
            ValidationContext tempValidationContext = new ValidationContext();
            return ImmutableList.of(APPLICATION_JSON_TYPE, tempValidationContext.streamingResponseMediaType(returnType));
        }
        return ImmutableList.of(APPLICATION_JSON_TYPE);
    }

    private Provider<JaxrsMethodFilter> jaxrsMethodFilterProvider()
    {
        Provider<ModelServices> modelServices = bindingProvider.get(ModelServices.class);
        Provider<Map<Method, ModelDeprecation>> deprecations = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Map<Predicate<ModelMethod>, ContainerRequestFilter>> requestFilters = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Map<Predicate<ModelMethod>, ContainerResponseFilter>> responseFilters = bindingProvider.get(new TypeLiteral<>() {});
        return () -> new JaxrsMethodFilter(modelServices.get(), deprecations.get(), requestFilters.get(), responseFilters.get());
    }

    private Provider<JaxrsQuotaFilter> jaxrsQuotaFilterProvider()
    {
        Provider<ModelServices> modelServices = bindingProvider.get(ModelServices.class);
        return () -> new JaxrsQuotaFilter(modelServices.get());
    }

    private Provider<JaxrsApiUsageLogFilter> jaxrsApiUsageLogFilterProvider()
    {
        Provider<ModelServices> modelServices = bindingProvider.get(ModelServices.class);
        return () -> new JaxrsApiUsageLogFilter(modelServices.get());
    }

    private Provider<SpecialApiTypeValueParamProvider> specialApiTypeValueParamProvider()
    {
        Provider<Map<Class<? extends ApiId<?, ?>>, ApiIdLookup<? extends ApiId<?, ?>>>> idLookups = bindingProvider.get(new TypeLiteral<>() {});
        Provider<JsonMapper> jsonMapper = bindingProvider.getUnqualified(JsonMapper.class);
        return () -> new SpecialApiTypeValueParamProvider(idLookups.get(), jsonMapper.get());
    }

    private Provider<JaxrsBindingBridge> jaxrsBindingBridgeProvider()
    {
        Provider<Map<ModelService, Object>> services = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Set<ModelServiceType>> serviceTypes = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Optional<OpenApiProvider>> openApiProvider = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Optional<OpenApiFilter>> openApiFilter = bindingProvider.get(new TypeLiteral<>() {});
        Provider<Optional<OpenApiMetadata>> openApiMetadata = bindingProvider.get(new TypeLiteral<>() {});
        return () -> new JaxrsBindingBridge(services.get(), serviceTypes.get(), openApiProvider.get(), openApiFilter.get(), openApiMetadata.get());
    }

    private Provider<JaxrsMapper> jaxrsMapperProvider()
    {
        Provider<JsonMapper> jsonMapper = bindingProvider.getUnqualified(JsonMapper.class);
        Provider<PatchFieldsBuilder> patchFieldsBuilder = bindingProvider.get(PatchFieldsBuilder.class);
        return () -> new JaxrsMapper(jsonMapper.get(), patchFieldsBuilder.get());
    }

    private Provider<JaxrsStatusValidator> jaxrsStatusValidatorProvider()
    {
        Provider<ApiMode> apiMode = bindingProvider.get(ApiMode.class);
        Provider<ModelServices> modelServices = bindingProvider.get(ModelServices.class);
        return () -> new JaxrsStatusValidator(apiMode.get(), modelServices.get());
    }

    private <T> void bindAnnotated(Class<T> implementation)
    {
        if (bindingAnnotation.isPresent()) {
            binder.bind(implementation).annotatedWith(bindingAnnotation.orElseThrow()).to(implementation).in(Scopes.SINGLETON);
            return;
        }
        binder.bind(implementation).in(Scopes.SINGLETON);
    }
}
