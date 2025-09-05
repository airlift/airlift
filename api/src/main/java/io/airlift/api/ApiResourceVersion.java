package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import static io.airlift.api.responses.ApiException.badRequest;

public record ApiResourceVersion(long version)
{
    public static final String PUBLIC_NAME = "syncToken";

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ApiResourceVersion(String str)
    {
        this(parseLong(str));
    }

    public ApiResourceVersion()
    {
        this(1);
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public ApiResourceVersion {}

    @JsonValue
    public String syncToken()
    {
        return Long.toHexString(version);
    }

    @JsonIgnore
    public long version()
    {
        return version;
    }

    private static long parseLong(String str)
    {
        try {
            return Long.parseUnsignedLong(str, 16);
        }
        catch (NumberFormatException e) {
            throw badRequest("Invalid syncToken");
        }
    }
}
