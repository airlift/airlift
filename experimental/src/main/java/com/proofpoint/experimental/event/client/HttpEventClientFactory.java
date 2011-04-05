package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.List;

public class HttpEventClientFactory implements EventClientFactory
{
    private final Provider<ObjectMapper> objectMapperProvider;
    private final HttpEventClientConfig httpEventClientConfig;
    private final AsyncHttpClient client;

    @Inject
    public HttpEventClientFactory(Provider<ObjectMapper> objectMapperProvider, HttpEventClientConfig httpEventClientConfig)
    {
        Preconditions.checkNotNull(objectMapperProvider, "objectMapperProvider is null");
        Preconditions.checkNotNull(httpEventClientConfig, "httpEventClientConfig is null");

        this.objectMapperProvider = objectMapperProvider;
        this.httpEventClientConfig = httpEventClientConfig;

        // Build HTTP client config
        AsyncHttpClientConfig.Builder configBuilder = new AsyncHttpClientConfig.Builder()
                .setConnectionTimeoutInMs((int) httpEventClientConfig.getConnectTimeout().toMillis())
                .setMaximumConnectionsTotal(httpEventClientConfig.getMaxConnections())
                .setRequestTimeoutInMs((int) httpEventClientConfig.getRequestTimeout().toMillis());

        if (httpEventClientConfig.isCompress()) {
            configBuilder.setRequestCompressionLevel(6);
        }

        // Create client
        this.client = new AsyncHttpClient(configBuilder.build());
    }

    @Override
    public <T> HttpEventClient<T> createEventClient(List<EventTypeMetadata<? extends T>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        return new HttpEventClient<T>(httpEventClientConfig, objectMapperProvider.get(), client, eventTypes);
    }
}
