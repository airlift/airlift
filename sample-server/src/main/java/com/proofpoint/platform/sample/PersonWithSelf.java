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
package com.proofpoint.platform.sample;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class PersonWithSelf
{
    private final Person person;
    private final URI self;

    public static PersonWithSelf from(Person person, URI self)
    {
        return new PersonWithSelf(person, self);
    }

    private PersonWithSelf(Person person, URI self)
    {
        this.person = person;
        this.self = checkNotNull(self);
    }

    @JsonProperty
    public String getEmail()
    {
        return person.getEmail();
    }

    @JsonProperty
    public String getName()
    {
        return person.getName();
    }

    @JsonProperty
    public URI getSelf()
    {
        return self;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(person, self);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PersonWithSelf other = (PersonWithSelf) obj;
        return Objects.equal(this.person, other.person) && Objects.equal(this.self, other.self);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("person", person)
                .add("self", self)
                .toString();
    }
}
