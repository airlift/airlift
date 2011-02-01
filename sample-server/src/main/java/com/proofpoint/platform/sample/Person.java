package com.proofpoint.platform.sample;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.annotation.concurrent.Immutable;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Immutable
public class Person
{
    private final String email;
    private final String name;

    public Person(String email, String name)
    {
        Preconditions.checkNotNull(email, "email is null");
        Preconditions.checkNotNull(email, "name is null");

        this.email = email;
        this.name = name;
    }

    public String getEmail()
    {
        return email;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Person person = (Person) o;

        if (email != null ? !email.equals(person.email) : person.email != null) {
            return false;
        }
        if (name != null ? !name.equals(person.name) : person.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = email != null ? email.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
