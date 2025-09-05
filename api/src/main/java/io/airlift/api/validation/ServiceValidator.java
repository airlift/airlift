package io.airlift.api.validation;

import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildFullPath;

public interface ServiceValidator
{
    static void validateService(ValidationContext validationContext, ModelService modelService)
    {
        validateMetadata(modelService.serviceClass(), modelService.service());

        validationContext.inContext("Service: " + modelService.serviceClass(), subContext -> {
            subContext.validateName(modelService.service().type().id(), ValidationContext.NameType.STANDARD);
            subContext.validateDocumentationLinks(modelService.service().documentationLinks());
        });
    }

    static void validateUniqueUris(ValidationContext validationContext, Collection<ModelService> services)
    {
        validationContext.inContext("URI checks", _ -> {
            Map<String, List<UriCounter>> grouped = services.stream()
                    .flatMap(modelService -> modelService.methods().stream().flatMap(modelMethod -> buildUriCounter(validationContext, modelService, modelMethod)))
                    .collect(Collectors.groupingBy(UriCounter::uri));
            List<String> duplicates = grouped.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .map(entry -> "Service with duplicate URI (%s) were found: %s".formatted(entry.getKey(), methodList(entry.getValue())))
                    .collect(toImmutableList());
            if (!duplicates.isEmpty()) {
                throw new ValidatorException(duplicates);
            }
        });
    }

    private static Stream<UriCounter> buildUriCounter(ValidationContext validationContext, ModelService modelService, ModelMethod modelMethod)
    {
        return validationContext.withContext("Method " + modelMethod.method(), _ -> new UriCounter(modelMethod.httpMethod() + ":" + buildFullPath(modelService.service(), modelMethod, FOR_DISPLAY), modelMethod))
                .stream();
    }

    private static void validateMetadata(Class<?> clazz, ModelServiceMetadata metadata)
    {
        List<String> errors = new ArrayList<>();
        if (metadata.type().title().isBlank()) {
            errors.add("Service Type title is empty or null: " + clazz);
        }
        if (metadata.type().description().isBlank()) {
            errors.add("Service Type description is empty or null: " + clazz);
        }
        if (metadata.type().id().isBlank()) {
            errors.add("Service Type id is empty or null: " + clazz);
        }
        if (metadata.type().version() < 0) {
            errors.add("Service Type version must be a positive number: " + clazz);
        }
        if (!errors.isEmpty()) {
            throw new ValidatorException(errors);
        }
    }

    static String methodList(List<UriCounter> counters)
    {
        return counters.stream().map(counter -> methodName(counter.modelMethod().method())).collect(Collectors.joining(", "));
    }

    static String methodName(Method method)
    {
        return "%s#%s".formatted(method.getDeclaringClass().getName(), method.getName());
    }

    record UriCounter(String uri, ModelMethod modelMethod) {}
}
