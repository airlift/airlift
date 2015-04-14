package io.airlift.security;

public enum AuthScheme
{
    NEGOTIATE("negotiate");

    private final String value;

    private AuthScheme(final String value)
    {
        this.value = value;
    }

    public static AuthScheme fromString(String value)
    {
        for (AuthScheme scheme : AuthScheme.values()) {
            if (scheme.value.equalsIgnoreCase(value)) {
                return scheme;
            }
        }

        throw new IllegalArgumentException(String.format("Invalid value %s for %s",
                value, AuthScheme.class.getSimpleName()));
    }

    @Override
    public String toString()
    {
        return value;
    }
}
