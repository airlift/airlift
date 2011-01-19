package com.proofpoint.sample;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@JsonAutoDetect(JsonMethod.NONE)
public class Person
{
    private final String email;
    private final String name;

    @JsonCreator
    public Person(@JsonProperty("email") String email, @JsonProperty("name") String name)
            throws ValidationException
    {
        this.email = email;
        this.name = name;

        validate().throwIfHasErrors();
    }

    private Errors validate()
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return Errors.forViolations(validator.validate(this));
    }

    @JsonProperty
    @NotNull(message = "is missing")
    @Pattern(regexp = "[^@]+@[^@]+", message = "is malformed")
    public String getEmail()
    {
        return email;
    }

    @JsonProperty
    @NotNull(message = "is missing")
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

    @Override
    public String toString()
    {
        return "Person{" +
                "email='" + email + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
