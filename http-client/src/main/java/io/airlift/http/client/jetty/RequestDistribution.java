package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import java.util.List;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

class RequestDistribution extends CachedDistribution {
    interface Processor {
        void process(Distribution distribution, JettyRequestListener listener, long now);
    }

    public RequestDistribution(HttpClient httpClient, Processor processor) {
        super(() -> {
            long now = System.nanoTime();
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .map(JettyHttpClient::getRequestListenersForDestination)
                    .flatMap(List::stream)
                    .forEach(listener -> processor.process(distribution, listener, now));
            return distribution;
        });
    }
}
