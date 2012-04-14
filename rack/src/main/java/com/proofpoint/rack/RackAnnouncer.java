package com.proofpoint.rack;

import com.google.inject.Inject;
import com.proofpoint.discovery.client.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.Announcer;
import com.proofpoint.discovery.client.ServiceAnnouncement;

import static com.proofpoint.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import static com.proofpoint.discovery.client.ServiceAnnouncement.serviceAnnouncement;

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
            builder.addProperty("https-external", httpServerInfo.getHttpsExternalUri().toString());
        }
        return builder.build();
    }
}
