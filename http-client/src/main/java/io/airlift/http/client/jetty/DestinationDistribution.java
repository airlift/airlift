package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

class DestinationDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, HttpDestination destination);
    }

    public DestinationDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .forEach(destination -> processor.process(distribution, destination));
            return distribution;
        });
    }
}
