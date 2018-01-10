package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.PoolingHttpDestination;

import java.util.List;

class RequestDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, JettyRequestListener listener, long now);
    }

    public RequestDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            long now = System.nanoTime();
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(PoolingHttpDestination.class::isInstance)
                    .map(PoolingHttpDestination.class::cast)
                    .map(JettyHttpClient::getRequestListenersForDestination)
                    .flatMap(List::stream)
                    .forEach(listener -> processor.process(distribution, listener, now));
            return distribution;
        });
    }
}
