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
package io.airlift.discovery.client;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

public class ServiceDescriptorsRepresentation
{
    private final String environment;
    private final List<ServiceDescriptor> serviceDescriptors;

    @JsonCreator
    public ServiceDescriptorsRepresentation(
            @JsonProperty("environment") String environment,
            @JsonProperty("services") List<ServiceDescriptor> serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptors);
        this.environment = environment;
        this.serviceDescriptors = ImmutableList.copyOf(serviceDescriptors);
    }

    @JsonProperty
    public String getEnvironment()
    {
        return environment;
    }

    @JsonProperty("services")
    public List<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("environment", environment)
                .add("serviceDescriptors", serviceDescriptors)
                .toString();
    }
}
