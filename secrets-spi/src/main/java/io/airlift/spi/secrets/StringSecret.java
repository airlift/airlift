package io.airlift.spi.secrets;

public record StringSecret(String value)
        implements Secret
{
    @Override
    public String toString()
    {
        return "StringSecret[value=REDACTED]";
    }
}
