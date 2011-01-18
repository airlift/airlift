package com.proofpoint.sample;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import javax.validation.ConstraintViolation;
import java.util.Set;

public class Errors
{
    private final Set<ConstraintViolation<?>> violations;

    private Errors(Set<? extends ConstraintViolation<?>> violations)
    {
        this.violations = ImmutableSet.copyOf(violations);
    }

    public static Errors forViolations(Set<? extends ConstraintViolation<?>> violations)
    {
        return new Errors(violations);
    }

    public void throwIfHasErrors()
    {
        if (!violations.isEmpty()) {
            throw getException();
        }
    }

    @VisibleForTesting
    ValidationException getException()
    {
        return new ValidationException(violations);
    }
}
