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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class Person
{
    private final String email;
    private final String name;

    public Person(String email, String name)
    {
        Preconditions.checkNotNull(email, "email is null");
        Preconditions.checkNotNull(name, "name is null");

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
    public int hashCode()
    {
        return Objects.hashCode(email, name);
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
        final Person other = (Person) obj;
        return Objects.equal(this.email, other.email) && Objects.equal(this.name, other.name);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("email", email)
                .add("name", name)
                .toString();
    }
}
