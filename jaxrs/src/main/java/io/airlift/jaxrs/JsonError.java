package io.airlift.jaxrs;

import com.google.common.base.CharMatcher;
import io.airlift.json.JsonCodec;

import static com.google.common.base.Verify.verify;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.Objects.requireNonNull;

public record JsonError(String code, String message)
{
    private static final CharMatcher MATCHER = CharMatcher.inRange('A', 'Z')
            .or(CharMatcher.is('_'))
            .precomputed();

    public JsonError
    {
        verify(MATCHER.matchesAllOf(code), "Error code must only contain uppercase letters and underscores: %s", code);
        requireNonNull(code, "code is null");
        requireNonNull(message, "message is null");
    }

    public static JsonCodec<JsonError> codec()
    {
        return jsonCodec(JsonError.class);
    }
}
