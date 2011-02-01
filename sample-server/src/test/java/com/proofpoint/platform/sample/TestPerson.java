package com.proofpoint.platform.sample;

import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

public class TestPerson
{
    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(asList(new Person("foo@example.com", "Mr Foo"), new Person("foo@example.com", "Mr Foo")),
                                asList(new Person("bar@example.com", "Mr Bar"), new Person("bar@example.com", "Mr Bar")),
                                asList(new Person("foo@example.com", "Mr Bar"), new Person("foo@example.com", "Mr Bar")),
                                asList(new Person("bar@example.com", "Mr Foo"), new Person("bar@example.com", "Mr Foo")));
    }
}
