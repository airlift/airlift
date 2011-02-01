package com.proofpoint.platform.sample;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.net.URI;

@JsonAutoDetect(JsonMethod.NONE)
public class PersonRepresentation
{
    private final Person person;
    private final URI self;

    public static PersonRepresentation from(Person person, URI self)
    {
        return new PersonRepresentation(person, self);
    }

    @JsonCreator
    public PersonRepresentation(@JsonProperty("email") String email, @JsonProperty("name") String name, @JsonProperty("self") URI self)
    {
        this(new Person(email, name), self);
    }

    private PersonRepresentation(Person person, URI self)
    {
        this.person = person;
        this.self = self;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    @Pattern(regexp = "[^@]+@[^@]+", message = "is malformed")
    public String getEmail()
    {
        return person.getEmail();
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getName()
    {
        return person.getName();
    }

    public URI getSelf()
    {
        return self;
    }

    public Person toPerson()
    {
        return person;
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

        PersonRepresentation that = (PersonRepresentation) o;

        if (person != null ? !person.equals(that.person) : that.person != null) {
            return false;
        }
        if (self != null ? !self.equals(that.self) : that.self != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = person != null ? person.hashCode() : 0;
        result = 31 * result + (self != null ? self.hashCode() : 0);
        return result;
    }
}
