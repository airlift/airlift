package io.airlift.http.client;

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public final class HeaderName
{
    private final String original;
    private final String lowerCase;

    public static HeaderName of(String value)
    {
        return new HeaderName(value);
    }

    private HeaderName(String value)
    {
        requireNonNull(value, "value is null");
        this.original = value;
        this.lowerCase = value.toLowerCase(ENGLISH);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        HeaderName other = (HeaderName) obj;
        return lowerCase.equals(other.lowerCase);
    }

    @Override
    public int hashCode()
    {
        return lowerCase.hashCode();
    }

    @Override
    public String toString()
    {
        return original;
    }
}
