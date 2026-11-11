package io.airlift.spi.secrets;

import java.util.Map;

public record HttpHeadersSecret(Map<String, String> headers)
        implements Secret
{
    @Override
    public String toString()
    {
        return "HttpHeadersSecret[headers=[REDACTED]]";
    }
}
