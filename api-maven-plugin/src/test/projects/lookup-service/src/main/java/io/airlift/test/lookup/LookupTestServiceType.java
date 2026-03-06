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
package io.airlift.test.lookup;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;

import java.util.Set;

public class LookupTestServiceType
        implements ApiServiceType
{
    @Override
    public String id()
    {
        return "lookuptest";
    }

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String title()
    {
        return "Lookup Test API";
    }

    @Override
    public String description()
    {
        return "API for testing lookup ID validation in api-maven-plugin";
    }

    @Override
    public Set<ApiServiceTrait> traits()
    {
        return ImmutableSet.of();
    }
}
