/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigMap;

import java.net.URI;
import java.util.Map;

public class ReportClientConfig
{
    private URI uri = null;
    private Map<String, String> tags = ImmutableMap.of();

    public URI getUri()
    {
        return uri;
    }

    @Config("report.uri")
    public ReportClientConfig setUri(URI uri)
    {
        this.uri = uri;
        return this;
    }

    public Map<String, String> getTags()
    {
        return tags;
    }

    @Config("report.tag")
    @ConfigMap
    public ReportClientConfig setTags(Map<String, String> tags)
    {
        this.tags = tags;
        return this;
    }
}
