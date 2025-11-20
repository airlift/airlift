package io.airlift.mcp.tasks;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.net.URLDecoder.decode;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public record CombinedIds<A, B>(A a, B b)
{
    private static final String SEPARATOR = "&";

    static {
        checkState(!encode(SEPARATOR, UTF_8).equals(SEPARATOR), "SEPARATOR must require encoding");
    }

    public static String combineIds(String a, String b)
    {
        return encode(a, UTF_8) + SEPARATOR + encode(b, UTF_8);
    }

    public static <A, B> CombinedIds<A, B> splitIds(String combinedId, Function<String, A> aMapper, Function<String, B> bMapper)
    {
        String[] parts = combinedId.split(SEPARATOR, 2);
        checkArgument(parts.length == 2, "Invalid combinedId: %s", combinedId);

        A a = aMapper.apply(decode(parts[0], UTF_8));
        B b = bMapper.apply(decode(parts[1], UTF_8));

        return new CombinedIds<>(a, b);
    }
}
