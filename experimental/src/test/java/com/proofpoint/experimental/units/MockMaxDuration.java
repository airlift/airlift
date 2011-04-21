package com.proofpoint.experimental.units;

import com.proofpoint.units.Duration;

import javax.validation.Payload;
import java.lang.annotation.Annotation;

class MockMaxDuration
    implements MaxDuration
{
    private final Duration duration;

    public MockMaxDuration(Duration duration)
    {
        this.duration = duration;
    }

    @Override
    public String value()
    {
        return duration.toString();
    }

    @Override
    public String message()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?>[] groups()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Payload>[] payload()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Annotation> annotationType()
    {
        return MaxDuration.class;
    }
}
