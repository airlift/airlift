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

import com.google.inject.Inject;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;

import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.ServiceAnnouncementBuilder;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;

public class RackAnnouncer
{
    @Inject
    public RackAnnouncer(RackServletConfig config, Announcer announcer, AnnouncementHttpServerInfo httpServerInfo)
    {
        if (config.getServiceAnnouncement() != null) {
            announcer.addServiceAnnouncement(createHttpAnnouncement(httpServerInfo, config.getServiceAnnouncement()));
        }
    }

    private static ServiceAnnouncement createHttpAnnouncement(AnnouncementHttpServerInfo httpServerInfo, String announcement)
    {
        ServiceAnnouncementBuilder builder = serviceAnnouncement(announcement);
        if (httpServerInfo.getHttpUri() != null) {
            builder.addProperty("http", httpServerInfo.getHttpUri().toString());
            builder.addProperty("http-external", httpServerInfo.getHttpExternalUri().toString());
        }
        if (httpServerInfo.getHttpsUri() != null) {
            builder.addProperty("https", httpServerInfo.getHttpsUri().toString());
        }
        return builder.build();
    }
}
