package io.airlift.rack;

import com.google.inject.Inject;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.discovery.client.Announcer;
import io.airlift.discovery.client.ServiceAnnouncement;

import static io.airlift.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;

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
