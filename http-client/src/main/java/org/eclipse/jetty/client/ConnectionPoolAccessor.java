package org.eclipse.jetty.client;

import java.util.Collection;

public final class ConnectionPoolAccessor
{
    private ConnectionPoolAccessor() {}

    public static Collection<Connection> getActiveConnections(AbstractConnectionPool pool)
    {
        return pool.getActiveConnections();
    }
}
