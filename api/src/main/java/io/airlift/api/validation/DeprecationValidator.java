package io.airlift.api.validation;

import com.google.common.annotations.VisibleForTesting;
import io.airlift.api.ApiDeprecated;
import io.airlift.api.model.ModelDeprecation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Optional;

import static io.airlift.api.validation.ServiceValidator.methodName;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

public interface DeprecationValidator
{
    static void validateDeprecations(ValidationContext validationContext, Collection<ModelDeprecation> deprecations)
    {
        deprecations.forEach(deprecation -> validationContext.inContext("Deprecation of " + methodName(deprecation.method()), _ -> {
            if (deprecation.deprecationDate().isPresent()) {
                if (deprecation.deprecationDate().orElseThrow().isBefore(Instant.now())) {
                    throw new ValidatorException("Deprecation date for %s is in the past".formatted(methodName(deprecation.method())));
                }
            }
            if (deprecation.newImplementation().isPresent()) {
                if (deprecation.newImplementation().orElseThrow().isBlank()) {
                    throw new ValidatorException("New implementation for %s is blank".formatted(methodName(deprecation.method())));
                }
            }
        }));
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
