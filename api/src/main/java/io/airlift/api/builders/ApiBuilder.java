package io.airlift.api.builders;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServices;
import io.airlift.api.validation.ValidationContext;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import static io.airlift.api.ApiBuilderConfig.DEFAULT_CONTEXT_ANNOTATIONS;
import static io.airlift.api.validation.DeprecationValidator.validateDeprecations;
import static io.airlift.api.validation.MethodValidator.validateMethod;
import static io.airlift.api.validation.ResourceValidator.validateParameter;
import static io.airlift.api.validation.ResourceValidator.validateRequestBody;
import static io.airlift.api.validation.ResourceValidator.validateResource;
import static io.airlift.api.validation.ResourceValidator.validateResult;
import static io.airlift.api.validation.ServiceValidator.validateService;
import static io.airlift.api.validation.ServiceValidator.validateUniqueUris;
import static java.util.Objects.requireNonNull;

public class ApiBuilder
{
    private final ValidationContext validationContext;
    private final ServicesBuilder servicesBuilder;
    private final ApiEnumValueResolver enumValueResolver;
    private final Set<Class<? extends Annotation>> contextAnnotations;

    private ApiBuilder(ValidationContext validationContext, ServicesBuilder servicesBuilder, ApiEnumValueResolver enumValueResolver, Set<Class<? extends Annotation>> contextAnnotations)
    {
        this.validationContext = requireNonNull(validationContext, "validationContext is null");
        this.servicesBuilder = requireNonNull(servicesBuilder, "servicesBuilder is null");
        this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
        this.contextAnnotations = ImmutableSet.copyOf(contextAnnotations);
    }

    public static ApiBuilder apiBuilder()
    {
        return apiBuilder(ApiBuilderConfig.jackson());
    }

    public static ApiBuilder apiBuilder(ApiBuilderConfig config)
    {
        requireNonNull(config, "config is null");
        return apiBuilder(config.enumValueResolver(), config.contextAnnotations());
    }

    public static ApiBuilder apiBuilder(ApiEnumValueResolver enumValueResolver)
    {
        return apiBuilder(enumValueResolver, DEFAULT_CONTEXT_ANNOTATIONS);
    }

    private static ApiBuilder apiBuilder(ApiEnumValueResolver enumValueResolver, Set<Class<? extends Annotation>> contextAnnotations)
    {
        return new ApiBuilder(new ValidationContext(), ServicesBuilder.servicesBuilder(enumValueResolver, contextAnnotations), enumValueResolver, contextAnnotations);
    }

    public ApiBuilder add(Class<?> serviceClass)
    {
        validationContext.inContext("Service: " + serviceClass, _ -> servicesBuilder.add(serviceClass));
        return this;
    }

    public ModelApi build()
    {
        Optional<ModelServices> maybeServices = validationContext.withContext("API", context -> {
            ModelServices modelServices = servicesBuilder.build();

            validate(context, modelServices);

            return modelServices;
        });

        Set<String> errors = validationContext.errors();

        ModelServices modelServices = maybeServices.map(services -> services.withErrors(errors))
                .orElseGet(() -> new ModelServices(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), errors));

        return new ModelApi(modelServices, validationContext.resourcesWithUnwrappedComponents(), validationContext.polyResources(), validationContext.needsSerializationValidation());
    }

    private void validate(ValidationContext context, ModelServices modelServices)
    {
        modelServices.services().forEach(modelService -> {
            validateService(context, modelService);

            modelService.methods().forEach(modelMethod -> {
                validateMethod(context, modelMethod, modelService.service().type().serviceTraits(), contextAnnotations);
                validateResult(context, modelService.service(), modelMethod, enumValueResolver);

                modelMethod.responses().forEach(modelResponse -> validateResource(context, modelService.service(), modelResponse.resource(), enumValueResolver));

                modelMethod.parameters().stream()
                        .flatMap(parameter -> parameter.components().stream())
                        .forEach(component -> validateParameter(context, modelService.service(), modelMethod, component.name(), component, enumValueResolver));

                modelMethod.requestBody().ifPresent(requestBody -> validateRequestBody(context, modelService.service(), modelMethod, requestBody, enumValueResolver));
            });
        });

        validateUniqueUris(context, modelServices.services());
        validateDeprecations(context, modelServices.deprecations());
    }
}
