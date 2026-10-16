package io.airlift.http.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;

import java.util.Map;

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public final class HeaderName
{
    private static final HeaderName[] KNOWN_BY_ORDINAL;
    private static final Map<String, HeaderName> KNOWN_BY_NAME;

    static {
        HttpHeader[] values = HttpHeader.values();
        HeaderName[] byOrdinal = new HeaderName[values.length];
        ImmutableMap.Builder<String, HeaderName> byName = ImmutableMap.builderWithExpectedSize(values.length);
        for (HttpHeader header : values) {
            HeaderName name = new HeaderName(header.lowerCaseName());
            byOrdinal[header.ordinal()] = name;
            byName.put(name.lowerCase, name);
        }
        KNOWN_BY_ORDINAL = byOrdinal;
        KNOWN_BY_NAME = byName.build();
    }

    private final String lowerCase;

    @JsonCreator
    public static HeaderName of(String value)
    {
        String lowerCase = value.toLowerCase(ENGLISH);
        HeaderName cached = KNOWN_BY_NAME.get(lowerCase);
        return cached != null ? cached : new HeaderName(lowerCase);
    }

    static HeaderName of(HttpField httpField)
    {
        HttpHeader header = httpField.getHeader();
        if (header != null) {
            return KNOWN_BY_ORDINAL[header.ordinal()];
        }
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
    @JsonValue
    public String toString()
    {
        return lowerCase;
    }
}
