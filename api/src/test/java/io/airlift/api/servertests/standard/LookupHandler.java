package io.airlift.api.servertests.standard;

import io.airlift.api.ApiIdLookup;
import jakarta.ws.rs.core.Request;

import java.util.Optional;

public class LookupHandler
        implements ApiIdLookup<LookupId>
{
    @Override
    public Optional<LookupId> lookup(Request request, String requestValue)
    {
        if (requestValue.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LookupId(requestValue + "-name"));
    }
}
