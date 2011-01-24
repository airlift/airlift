package com.proofpoint.platform.sample;

import com.google.common.base.Joiner;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.util.Arrays;

import static java.lang.String.format;
import static org.testng.Assert.fail;

public class TestPerson
{
    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(Arrays.asList(new Person("foo@example.com", "Mr Foo"), new Person("foo@example.com", "Mr Foo")),
                                Arrays.asList(new Person("bar@example.com", "Mr Bar"), new Person("bar@example.com", "Mr Bar")),
                                Arrays.asList(new Person("foo@example.com", "Mr Bar"), new Person("foo@example.com", "Mr Bar")),
                                Arrays.asList(new Person("bar@example.com", "Mr Foo"), new Person("bar@example.com", "Mr Foo")));

    }

    @Test
    public void testDoesNotAllowNullEmail()
    {
        assertFailsValidation(NotNull.class, "email", new Runnable() {
            @Override
            public void run()
            {
                new Person(null, "Joe");
            }
        });
    }

    @Test
    public void testDoesNotAllowNullName()
    {
        assertFailsValidation(NotNull.class, "name", new Runnable() {
            @Override
            public void run()
            {
                new Person("foo@example.com", null);
            }
        });
    }

    @Test
    public void testValidatesEmailFormat()
    {
        assertFailsValidation(Pattern.class, "name", new Runnable() {
            @Override
            public void run()
            {
                new Person("foo", "Joe");
            }
        });
    }

    private static void assertFailsValidation(Class<? extends Annotation> annotation, String property, Runnable block)
    {
        try {
            block.run();
            fail("Expected ValidationException");
        }
        catch (ValidationException e) {
            if (e.getViolations().isEmpty()) {
                fail(format("expected validation to fail due to %s on %s, but no validations failed", annotation.getClass().getName(), property));
            }
            if (e.getViolations().size() > 1) {
                fail(format("expected validation to fail due to %s on %s, but multiple validations failed: %s",
                            annotation.getClass().getName(), property, Joiner.on(", ").join(e.getViolations())));

            }
        }
    }
}
