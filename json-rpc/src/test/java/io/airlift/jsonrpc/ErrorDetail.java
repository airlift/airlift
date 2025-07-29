package io.airlift.jsonrpc;

import static java.util.Objects.requireNonNull;

public record ErrorDetail(String detail)
{
    public ErrorDetail
    {
        requireNonNull(detail, "detail is null");
    }
}
