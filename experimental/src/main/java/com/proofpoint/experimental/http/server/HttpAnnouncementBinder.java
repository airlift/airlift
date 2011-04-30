package com.proofpoint.experimental.http.server;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.experimental.discovery.client.DiscoveryBinder;
import com.proofpoint.experimental.discovery.client.ServiceAnnouncement;
import com.proofpoint.experimental.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.http.server.HttpServerInfo;

import static com.proofpoint.experimental.discovery.client.ServiceAnnouncement.serviceAnnouncement;

public class HttpAnnouncementBinder
{
    public static HttpAnnouncementBinder httpAnnouncementBinder(Binder binder)
    {
        return new HttpAnnouncementBinder(binder);
    }

    private final DiscoveryBinder discoveryBinder;

    private HttpAnnouncementBinder(Binder binder)
    {
        discoveryBinder = DiscoveryBinder.discoveryBinder(binder);
    }

    public ServiceAnnouncementBuilder bindHttpAnnouncement(String type)
    {
        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement(type);
        discoveryBinder.bindServiceAnnouncement(new HttpAnnouncementProvider(serviceAnnouncementBuilder));
        return serviceAnnouncementBuilder;
    }

    static class HttpAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        private final ServiceAnnouncementBuilder builder;
        private HttpServerInfo httpServerInfo;

        public HttpAnnouncementProvider(ServiceAnnouncementBuilder serviceAnnouncementBuilder)
        {
            builder = serviceAnnouncementBuilder;
        }

        @Inject
        public void setHttpServerInfo(HttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Override
        public ServiceAnnouncement get()
        {
            if (httpServerInfo.getHttpUri() != null) {
                builder.addProperty("http", httpServerInfo.getHttpUri().toString());
            }
            if (httpServerInfo.getHttpsUri() != null) {
                builder.addProperty("https", httpServerInfo.getHttpsUri().toString());
            }
            return builder.build();
        }
    }
}
