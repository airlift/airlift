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
package com.proofpoint.rack;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class RackServletConfig
{
    private String rackConfigPath = "rack/config.ru";
    private String serviceAnnouncement;

    @NotNull
    public String getRackConfigPath()
    {
        return rackConfigPath;
    }

    @Config("rackserver.rack-config-path")
    @ConfigDescription("A path to the rack application configuration file. For testing only.")
    public RackServletConfig setRackConfigPath(String rackConfigPath)
    {
        this.rackConfigPath = rackConfigPath;
        return this;
    }

    @Pattern(regexp = "[a-z][a-z0-9]{0,14}")
    public String getServiceAnnouncement()
    {
        return serviceAnnouncement;
    }

    @Config("rackserver.announcement")
    @ConfigDescription("name of service to announce")
    public RackServletConfig setServiceAnnouncement(String serviceAnnouncement)
    {
        this.serviceAnnouncement = serviceAnnouncement;
        return this;
    }
}
