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
package io.airlift.api.maven;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class DefaultServiceType
        implements ApiServiceType
{
    private final String id;
    private final int version;
    private final String title;
    private final String description;

    public DefaultServiceType()
    {
        this("default", 1, "API", "Generated API");
    }

    public DefaultServiceType(String id, int version, String title, String description)
    {
        this.id = requireNonNull(id, "id is null");
        checkArgument(version > 0, "version must be positive");
        this.version = version;
        this.title = requireNonNull(title, "title is null");
        this.description = requireNonNull(description, "description is null");
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public int version()
    {
        return version;
    }

    @Override
    public String title()
    {
        return title;
    }

    @Override
    public String description()
    {
        return description;
    }

    @Override
    public Set<ApiServiceTrait> traits()
    {
        return ImmutableSet.of();
    }
}
