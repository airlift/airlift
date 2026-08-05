/*
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
package io.airlift.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;

public class Person
{
    private String name;
    private boolean rocks;
    private Optional<String> lastName;

    @JsonProperty
    public String getName()
    {
        return name;
    }

    @JsonProperty
    public Person setName(String name)
    {
        this.name = name;
        return this;
    }

    @JsonProperty
    public boolean isRocks()
    {
        return rocks;
    }

    @JsonProperty
    public Person setRocks(boolean rocks)
    {
        this.rocks = rocks;
        return this;
    }

    @JsonProperty
    public Optional<String> getLastName()
    {
        return lastName;
    }

    @JsonProperty
    public void setLastName(Optional<String> lastName)
    {
        this.lastName = lastName;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        Person o = (Person) obj;
        return Objects.equals(this.name, o.name) &&
                this.rocks == o.rocks;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, rocks);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .add("rocks", rocks)
                .add("lastName", lastName)
                .toString();
    }
}
