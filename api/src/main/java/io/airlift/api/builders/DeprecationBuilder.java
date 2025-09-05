package io.airlift.api.builders;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiDeprecated;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildFullPath;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

public class DeprecationBuilder
{
    private DeprecationBuilder() {}

    public static Set<ModelDeprecation> buildDeprecations(Collection<ModelService> services)
    {
        ImmutableSet.Builder<ModelDeprecation> builder = ImmutableSet.builder();
        services.forEach(modelService -> modelService.methods().forEach(modelMethod -> {
            ApiDeprecated deprecated = modelMethod.method().getDeclaredAnnotation(ApiDeprecated.class);
            if (deprecated != null) {
                builder.add(fromAnnotation(services, modelMethod.method(), deprecated));
            }
        }));
        return builder.build();
    }

    private static ModelDeprecation fromAnnotation(Collection<ModelService> services, Method method, ApiDeprecated deprecated)
    {
        Optional<Instant> deprecationDate = toDeprecationDate(deprecated);
        Optional<String> newImplementation = toNewImplementation(services, deprecated.newImplementationClass(), deprecated.newImplementationMethod());
        return new ModelDeprecation(method, deprecated.information(), deprecationDate, newImplementation);
    }

    private static Optional<String> toNewImplementation(Collection<ModelService> services, Class<?> newImplementationClass, String newImplementationMethod)
    {
        if (newImplementationClass == Object.class) {
            if (newImplementationMethod.isBlank()) {
                return Optional.empty();
            }
            throw new ValidatorException("@%s annotation newImplementationClass must be specified when newImplementationMethod is not blank".formatted(ApiDeprecated.class.getSimpleName()));
        }
        else if (newImplementationMethod.isBlank()) {
            throw new IllegalArgumentException("When @%s annotation newImplementationClass is specified when newImplementationMethod cannot be blank".formatted(ApiDeprecated.class.getSimpleName()));
        }

        record Holder(ModelService service, ModelMethod modelMethod) {}

        List<Holder> holders = services.stream()
                .filter(service -> service.serviceClass().equals(newImplementationClass))
                .flatMap(service -> service.methods()
                        .stream()
                        .filter(modelMethod -> modelMethod.method().getName().equals(newImplementationMethod))
                        .map(modelMethod -> new Holder(service, modelMethod)))
                .collect(toImmutableList());
        return switch (holders.size()) {
            case 0 -> throw new ValidatorException("Could not find new implementation specified by @%s. %s$%s".formatted(ApiDeprecated.class.getSimpleName(), newImplementationClass.getName(), newImplementationMethod));
            case 1 -> Optional.of(buildFullPath(holders.getFirst().service.service(), holders.getFirst().modelMethod, FOR_DISPLAY));
            default -> throw new ValidatorException("Found multiple new implementations specified by @%s. %s$%s".formatted(ApiDeprecated.class.getSimpleName(), newImplementationClass.getName(), newImplementationMethod));
        };
    }

    @VisibleForTesting
    static Optional<Instant> toDeprecationDate(ApiDeprecated deprecated)
    {
        if (deprecated.deprecationDate().isBlank()) {
            return Optional.empty();
        }
        try {
            TemporalAccessor parsed = ISO_LOCAL_DATE.parse(deprecated.deprecationDate());
            LocalDateTime localDateTime = LocalDateTime.of(LocalDate.from(parsed), LocalTime.MIDNIGHT.minusSeconds(1));
            return Optional.of(localDateTime.toInstant(ZoneOffset.UTC));
        }
        catch (DateTimeParseException ignore) {
            throw new ValidatorException("@%s annotation has an unrecognized deprecation date format \"%s\"".formatted(ApiDeprecated.class.getSimpleName(), deprecated.deprecationDate()));
        }
    }
}
