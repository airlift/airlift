package io.airlift.discovery.client;

import java.net.URI;
import java.util.List;

public interface HttpServiceSelector
{
    String getType();
    String getPool();
    List<URI> selectHttpService();
}
