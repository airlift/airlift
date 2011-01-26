package com.proofpoint.platform.sample;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.proofpoint.testing.EquivalenceTester;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
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
        assertFailsValidation(NotNull.class, "email", new Runnable()
        {
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
        assertFailsValidation(NotNull.class, "name", new Runnable()
        {
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
        assertFailsValidation(Pattern.class, "name", new Runnable()
        {
            @Override
            public void run()
            {
                new Person("foo", "Joe");
            }
        });
    }

    @Test
    public void testJsonRoundTrip() {
        Person expected = new Person("alice@example.com", "Alice");
        String json = toJson(expected);
        Person actual = fromJson(json, Person.class);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        Person expected = new Person("foo@example.com", "Mr Foo");

        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        Person actual = fromJson(json, Person.class);

        assertEquals(actual, expected);
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

    private static <T> T fromJson(String json, Class<T> type)
    {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("Invalid %s json string", type.getSimpleName()), e);
        }
    }

    private static String toJson(Object object)
            throws IllegalArgumentException
    {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(object);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("%s could not be converted to json", object.getClass().getSimpleName()), e);
        }
    }
}
