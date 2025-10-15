package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import java.util.Objects;
import org.eclipse.jetty.client.AbstractConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

class ConnectionPoolDistribution extends CachedDistribution {
    interface Processor {
        void process(Distribution distribution, AbstractConnectionPool pool);
    }

    public ConnectionPoolDistribution(HttpClient httpClient, Processor processor) {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .map(HttpDestination::getConnectionPool)
                    .filter(Objects::nonNull)
                    .filter(AbstractConnectionPool.class::isInstance)
                    .map(AbstractConnectionPool.class::cast)
                    .forEach(pool -> processor.process(distribution, pool));
            return distribution;
        });
    }
}
