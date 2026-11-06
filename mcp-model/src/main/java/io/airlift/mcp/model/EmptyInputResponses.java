package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

final class EmptyInputResponses
        implements InputResponses<EmptyInputResponses>
{
    @Override
    public Optional<String> requestState()
    {
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, Object>> inputResponses()
    {
        return Optional.empty();
    }

    @Override
    public EmptyInputResponses withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses)
    {
        throw new UnsupportedOperationException();
    }
}
