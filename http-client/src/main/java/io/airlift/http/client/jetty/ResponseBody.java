package io.airlift.http.client.jetty;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Response body held as a list of segments. Every segment except the last is completely filled.
 */
record ResponseBody(List<byte[]> segments, int length)
{
    static final ResponseBody EMPTY = new ResponseBody(List.of(), 0);

    ResponseBody
    {
        requireNonNull(segments, "segments is null");
    }

    /**
     * Returns the body as a single array when it occupies exactly one segment.
     */
    boolean isSingleArray()
    {
        return segments.size() == 1 && segments.getFirst().length == length;
    }
}
