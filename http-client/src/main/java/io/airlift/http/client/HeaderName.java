package io.airlift.http.client;

import org.eclipse.jetty.http.HttpField;

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public final class HeaderName
{
    private final String lowerCase;

    public static HeaderName of(String value)
    {
        return new HeaderName(value.toLowerCase(ENGLISH));
    }

    // Visible only to the Response interface
    static HeaderName of(HttpField httpField)
    {
        return new HeaderName(httpField.getLowerCaseName());
    }

    private HeaderName(String lowerCase)
    {
        this.lowerCase = requireNonNull(lowerCase, "lowerCase is null");
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
        return lowerCase;
    }
}
