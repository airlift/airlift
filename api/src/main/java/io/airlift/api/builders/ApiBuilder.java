package io.airlift.api.builders;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServices;
import io.airlift.api.validation.ValidationContext;

import java.util.Optional;
import java.util.Set;

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

    private ApiBuilder(ValidationContext validationContext, ServicesBuilder servicesBuilder)
    {
        this.validationContext = requireNonNull(validationContext, "validationContext is null");
        this.servicesBuilder = requireNonNull(servicesBuilder, "servicesBuilder is null");
    }

    public static ApiBuilder apiBuilder()
    {
        return new ApiBuilder(new ValidationContext(), ServicesBuilder.servicesBuilder());
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
                validateMethod(context, modelMethod, modelService.service().type().serviceTraits());
                validateResult(context, modelService.service(), modelMethod);

                modelMethod.responses().forEach(modelResponse -> validateResource(context, modelService.service(), modelResponse.resource()));

                modelMethod.parameters().stream()
                        .flatMap(parameter -> parameter.components().stream())
                        .forEach(component -> validateParameter(context, modelService.service(), modelMethod, component.name(), component));

                modelMethod.requestBody().ifPresent(requestBody -> validateRequestBody(context, modelService.service(), modelMethod, requestBody));
            });
        });

        validateUniqueUris(context, modelServices.services());
        validateDeprecations(context, modelServices.deprecations());
    }
}
