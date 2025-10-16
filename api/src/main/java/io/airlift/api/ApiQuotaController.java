package io.airlift.api;

import jakarta.ws.rs.core.Request;

public interface ApiQuotaController
{
    void recordQuotaUsage(Request request, String quotaKey);
}
