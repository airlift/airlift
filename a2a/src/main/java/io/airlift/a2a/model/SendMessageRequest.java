package io.airlift.a2a.model;

import java.util.Map;
import java.util.Optional;

public record SendMessageRequest(Optional<String> tenant, Message message, Optional<SendMessageConfiguration> configuration, Optional<Map<String, Object>> metadata)
        implements Tenant, Metadata
{
}
