package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.PoolingHttpDestination;

import java.util.Objects;

class ConnectionPoolDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, DuplexConnectionPool pool);
    }

    public ConnectionPoolDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(PoolingHttpDestination.class::isInstance)
                    .map(PoolingHttpDestination.class::cast)
                    .map(PoolingHttpDestination::getConnectionPool)
                    .filter(Objects::nonNull)
                    .map(DuplexConnectionPool.class::cast)
                    .forEach(pool -> processor.process(distribution, pool));
            return distribution;
        });
    }
}
