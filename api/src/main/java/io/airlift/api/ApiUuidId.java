package io.airlift.api;

import com.google.common.collect.ImmutableList;

import java.util.UUID;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

public abstract class ApiUuidId<RESOURCE>
        extends ApiId<RESOURCE, UUID>
{
    protected ApiUuidId(UUID id)
    {
        super(requireNonNull(id, "id is null"));
    }

    protected ApiUuidId(String id)
    {
        super(id);
    }

    public UUID value()
    {
        return toInternal();
    }

    @Override
    public UUID toInternal()
    {
        try {
            return UUID.fromString(id);
        }
        catch (IllegalArgumentException e) {
            throw badRequest("Invalid id: " + id, ImmutableList.of("id"));
        }
    }

    protected static UUID defaultUuidValue()
    {
        return new UUID(0L, 0L);
    }
}
