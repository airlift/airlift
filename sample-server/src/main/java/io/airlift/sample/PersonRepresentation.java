/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.sample;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import java.net.URI;

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

    @JsonProperty
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
