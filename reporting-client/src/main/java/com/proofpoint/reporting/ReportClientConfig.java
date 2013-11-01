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
import com.proofpoint.configuration.DefunctConfig;
import com.proofpoint.configuration.LegacyConfig;

import java.util.Map;

@DefunctConfig("report.uri")
public class ReportClientConfig
{
    private boolean enabled = true;
    private Map<String, String> tags = ImmutableMap.of();

    public boolean isEnabled()
    {
        return enabled;
    }

    @Config("reporting.enabled")
    public ReportClientConfig setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }

    public Map<String, String> getTags()
    {
        return tags;
    }

    @Config("reporting.tag")
    @LegacyConfig("report.tag")
    @ConfigMap
    public ReportClientConfig setTags(Map<String, String> tags)
    {
        this.tags = tags;
        return this;
    }
}
