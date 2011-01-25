package com.proofpoint.platform.sample;

import javax.validation.ConstraintViolation;
import java.util.Set;

public class ValidationException
        extends RuntimeException
{
    private final Set<ConstraintViolation<?>> violations;

    public ValidationException(Set<ConstraintViolation<?>> violations)
    {
        this.violations = violations;
    }

    public Set<ConstraintViolation<?>> getViolations()
    {
        return violations;
    }

}
