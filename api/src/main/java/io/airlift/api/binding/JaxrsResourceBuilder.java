package io.airlift.api.binding;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import io.airlift.api.ApiService;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.openapi.OpenApiFilter;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.validation.ValidationContext;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.log.Logger;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
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
    private static final Set<ModelMethod> alreadyLogged = new HashSet<>();

    private final Binder binder;
    private final JaxrsBinder jaxrsBinder;
    private final MapBinder<ModelService, Object> servicesBinder;

    static JaxrsResourceBuilder jaxrsResourceBuilder(Binder binder, boolean withApiLogging)
    {
        return new JaxrsResourceBuilder(binder, withApiLogging);
    }

    private JaxrsResourceBuilder(Binder binder, boolean withApiLogging)
    {
        jaxrsBinder = jaxrsBinder(binder);
        this.binder = requireNonNull(binder, "binder is null");

        servicesBinder = MapBinder.newMapBinder(binder, new TypeLiteral<ModelService>() {}, new TypeLiteral<>() {}).permitDuplicates();

        jaxrsBinder.bind(JaxrsMethodFilter.class);
        jaxrsBinder.bind(JaxrsQuotaFilter.class);
        if (withApiLogging) {
            jaxrsBinder.bind(JaxrsApiUsageLogFilter.class);
        }
    }

    void bindFeatures()
    {
        newOptionalBinder(binder, OpenApiProvider.class);
        newOptionalBinder(binder, OpenApiFilter.class);
        newOptionalBinder(binder, OpenApiMetadata.class);

        binder.bind(PatchFieldsBuilder.class);
        jaxrsBinder.bind(SpecialApiTypeValueParamProvider.class);
        jaxrsBinder.bind(PaginationValueParamProvider.class);
        jaxrsBinder.bind(JaxrsBindingBridge.class);
        jaxrsBinder.bind(JaxrsMapper.class);
        jaxrsBinder.bind(ApiStreamResponseWriter.class);
        jaxrsBinder.bind(JaxrsStatusValidator.class);
    }

    void bindService(ModelService service)
    {
        binder.bind(service.serviceClass()).in(Scopes.SINGLETON);
        servicesBinder.addBinding(service).to(service.serviceClass()).in(Scopes.SINGLETON);
        jaxrsBinder.bindInstance(buildService(service));
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
                methodBuilder.consumes(isMultiPartForm ? MULTIPART_FORM_DATA_TYPE : APPLICATION_JSON_TYPE).produces(APPLICATION_JSON_TYPE);
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
}
