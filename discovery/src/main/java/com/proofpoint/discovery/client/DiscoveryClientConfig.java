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
package com.proofpoint.discovery.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.DefunctConfig;

import java.net.URI;

@DefunctConfig("discovery.uri")
public class DiscoveryClientConfig
{
    private URI discoveryServiceURI;

    @Deprecated
    public URI getDiscoveryServiceURI()
    {
        return discoveryServiceURI;
    }

    @Config("testing.discovery.uri")
    @ConfigDescription("Discovery service base URI (testing only)")
    @Deprecated
    public DiscoveryClientConfig setDiscoveryServiceURI(URI uri)
    {
        this.discoveryServiceURI = uri;
        return this;
    }
}
