package com.proofpoint.experimental.units;

import com.proofpoint.units.Duration;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class MaxDurationValidator
        implements ConstraintValidator<MaxDuration, Duration>
{
    private Duration max;

    @Override
    public void initialize(MaxDuration duration)
    {
        this.max = Duration.valueOf(duration.value());
    }

    @Override
    public boolean isValid(Duration duration, ConstraintValidatorContext context)
    {
        return duration == null || duration.compareTo(max) <= 0;
    }
}
