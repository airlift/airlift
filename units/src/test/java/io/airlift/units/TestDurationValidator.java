package io.airlift.units;

import com.google.common.base.Throwables;
import io.airlift.testing.Assertions;
import org.apache.bval.jsr303.ApacheValidationProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDurationValidator
{
    private static final Validator validator = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    @Test
    public void testMaxDurationValidator()
    {
        MaxDurationValidator maxValidator = new MaxDurationValidator();
        maxValidator.initialize(new MockMaxDuration(new Duration(5, TimeUnit.SECONDS)));

        assertTrue(maxValidator.isValid(new Duration(0, TimeUnit.SECONDS), new MockContext()));
        assertTrue(maxValidator.isValid(new Duration(5, TimeUnit.SECONDS), new MockContext()));
        assertFalse(maxValidator.isValid(new Duration(6, TimeUnit.SECONDS), new MockContext()));
    }


    @Test
    public void testMinDurationValidator()
    {
        MinDurationValidator minValidator = new MinDurationValidator();
        minValidator.initialize(new MockMinDuration(new Duration(5, TimeUnit.SECONDS)));

        assertTrue(minValidator.isValid(new Duration(5, TimeUnit.SECONDS), new MockContext()));
        assertTrue(minValidator.isValid(new Duration(6, TimeUnit.SECONDS), new MockContext()));
        assertFalse(minValidator.isValid(new Duration(0, TimeUnit.SECONDS), new MockContext()));
    }

    @Test
    public void testAllowsNullMinAnnotation()
    {
        validator.validate(new NullMinAnnotation());
    }

    @Test
    public void testAllowsNullMaxAnnotation()
    {
        validator.validate(new NullMaxAnnotation());
    }

    @Test
    public void testDetectsBrokenMinAnnotation()
    {
        try {
            validator.validate(new BrokenMinAnnotation());
            Assert.fail("expected a ValidationException caused by an IllegalArgumentException");
        }
        catch (ValidationException e) {
            Assertions.assertInstanceOf(Throwables.getRootCause(e), IllegalArgumentException.class);
        }
    }

    @Test
    public void testDetectsBrokenMaxAnnotation()
    {
        try {
            validator.validate(new BrokenMaxAnnotation());
            Assert.fail("expected a ValidationException caused by an IllegalArgumentException");
        }
        catch (ValidationException e) {
            Assertions.assertInstanceOf(Throwables.getRootCause(e), IllegalArgumentException.class);
        }
    }

    @Test
    public void testPassesValidation()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(7, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testFailsMaxDurationConstraint()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(11, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertEquals(violations.size(), 2);

        for (ConstraintViolation<ConstrainedDuration> violation : violations) {
            Assertions.assertInstanceOf(violation.getConstraintDescriptor().getAnnotation(), MaxDuration.class);
        }
    }

    @Test
    public void testFailsMinDurationConstraint()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(1, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertEquals(violations.size(), 2);

        for (ConstraintViolation<ConstrainedDuration> violation : violations) {
            Assertions.assertInstanceOf(violation.getConstraintDescriptor().getAnnotation(), MinDuration.class);
        }
    }

    private static class MockContext
            implements ConstraintValidatorContext
    {
        @Override
        public void disableDefaultConstraintViolation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDefaultConstraintMessageTemplate()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConstraintValidatorContext.ConstraintViolationBuilder buildConstraintViolationWithTemplate(String s)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class ConstrainedDuration
    {
        private final Duration duration;

        public ConstrainedDuration(Duration duration)
        {
            this.duration = duration;
        }

        @MinDuration("5s")
        public Duration getConstrainedByMin()
        {
            return duration;
        }

        @MaxDuration("10s")
        public Duration getConstrainedByMax()
        {
            return duration;
        }

        @MinDuration("5000ms")
        @MaxDuration("10000ms")
        public Duration getConstrainedByMinAndMax()
        {
            return duration;
        }
    }

    public static class NullMinAnnotation
    {
        @MinDuration("1s")
        public Duration getConstrainedByMin()
        {
            return null;
        }
    }

    public static class NullMaxAnnotation
    {
        @MaxDuration("1s")
        public Duration getConstrainedByMin()
        {
            return null;
        }
    }

    public static class BrokenMinAnnotation
    {
        @MinDuration("broken")
        public Duration getConstrainedByMin()
        {
            return new Duration(10, TimeUnit.SECONDS);
        }
    }

    public static class BrokenMaxAnnotation
    {
        @MinDuration("broken")
        public Duration getConstrainedByMin()
        {
            return new Duration(10, TimeUnit.SECONDS);
        }
    }

}
