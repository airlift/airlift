package io.airlift.http.client;

import com.google.common.base.Splitter;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HostAndPort;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Streams.stream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * An RFC-3986-compatible HTTP URI builder
 */
public class HttpUriBuilder
{
    private String scheme;
    private String host;
    private int port = -1;
    private String path = ""; // decoded path
    private final ListMultimap<String, String> params = LinkedListMultimap.create(); // decoded query params

    private static final Splitter QUERY_PARAM_SPLITTER = Splitter.on("&")
            .omitEmptyStrings();

    private static final Splitter QUERY_PARAM_VALUE_SPLITTER = Splitter.on("=")
            .limit(2);

    private static final byte[] PCHAR = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '-', '.', '_', '~', '!', '$', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '@',
    };

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private static final boolean[] ALLOWED_PATH_CHARS = buildAllowedTable(new byte[] {'/', '&'});
    private static final boolean[] ALLOWED_QUERY_CHARS = buildAllowedTable(new byte[] {'/', '?'});

    private HttpUriBuilder() {}

    private HttpUriBuilder(URI previous)
    {
        scheme = previous.getScheme();
        host = previous.getHost();
        port = previous.getPort();
        path = percentDecode(previous.getRawPath());
        params.putAll(parseParams(previous.getRawQuery()));
    }

    public static HttpUriBuilder uriBuilder()
    {
        return new HttpUriBuilder();
    }

    public static HttpUriBuilder uriBuilderFrom(URI uri)
    {
        requireNonNull(uri, "uri is null");
        checkArgument(uri.getScheme() != null, "URI does not have a scheme: %s", uri);
        checkArgument(uri.getHost() != null, "URI does not have a host: %s", uri);

        return new HttpUriBuilder(uri);
    }

    public HttpUriBuilder scheme(String scheme)
    {
        requireNonNull(scheme, "scheme is null");

        this.scheme = scheme;
        return this;
    }

    public HttpUriBuilder host(String host)
    {
        requireNonNull(host, "host is null");
        checkArgument(!host.startsWith("["), "host starts with a bracket");
        checkArgument(!host.endsWith("]"), "host ends with a bracket");
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        this.host = host;
        return this;
    }

    public HttpUriBuilder port(int port)
    {
        checkArgument(port >= 1 && port <= 65535, "port must be in the range 1-65535");
        this.port = port;
        return this;
    }

    public HttpUriBuilder defaultPort()
    {
        this.port = -1;
        return this;
    }

    public HttpUriBuilder hostAndPort(HostAndPort hostAndPort)
    {
        requireNonNull(hostAndPort, "hostAndPort is null");
        this.host = bracketedHostString(hostAndPort);
        this.port = hostAndPort.hasPort() ? hostAndPort.getPort() : -1;
        return this;
    }

    /**
     * Replace the current path with the given unencoded path
     */
    public HttpUriBuilder replacePath(String path)
    {
        requireNonNull(path, "path is null");

        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }

        this.path = path;
        return this;
    }

    /**
     * Append an unencoded path.
     * <p>
     * All reserved characters except '/' will be percent-encoded. '/' are considered as path separators and
     * appended verbatim.
     */
    public HttpUriBuilder appendPath(String path)
    {
        requireNonNull(path, "path is null");

        StringBuilder builder = new StringBuilder(this.path);
        if (!this.path.endsWith("/")) {
            builder.append('/');
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        builder.append(path);

        this.path = builder.toString();

        return this;
    }

    public HttpUriBuilder replaceParameter(String name, String... values)
    {
        return replaceParameter(name, asList(values));
    }

    public HttpUriBuilder replaceParameter(String name, Iterable<String> values)
    {
        requireNonNull(name, "name is null");

        params.removeAll(name);
        addParameter(name, values);

        return this;
    }

    public HttpUriBuilder addParameter(String name, String... values)
    {
        return addParameter(name, asList(values));
    }

    public HttpUriBuilder addParameter(String name, Iterable<String> values)
    {
        requireNonNull(name, "name is null");

        if (stream(values).findAny().isEmpty()) {
            params.put(name, null);
        }

        for (String value : values) {
            params.put(name, value);
        }

        return this;
    }

    // return an RFC-3986-compatible URI
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(scheme);
        builder.append("://");
        if (host != null) {
            builder.append(host);
        }
        if (!isDefaultPort()) {
            builder.append(':');
            builder.append(port);
        }

        String path = this.path;
        if (path.isEmpty() && !params.isEmpty()) {
            path = "/";
        }

        builder.append(encode(path, ALLOWED_PATH_CHARS));

        if (!params.isEmpty()) {
            builder.append('?');

            for (Iterator<Map.Entry<String, String>> iterator = params.entries().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, String> entry = iterator.next();

                builder.append(encode(entry.getKey(), ALLOWED_QUERY_CHARS));
                if (entry.getValue() != null) {
                    builder.append('=');
                    builder.append(encode(entry.getValue(), ALLOWED_QUERY_CHARS));
                }

                if (iterator.hasNext()) {
                    builder.append('&');
                }
            }
        }

        return builder.toString();
    }

    private boolean isDefaultPort()
    {
        return port == -1 ||
                ("http".equalsIgnoreCase(scheme) && port == 80) ||
                ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    public URI build()
    {
        checkState(scheme != null, "scheme has not been set");
        checkState(host != null, "host has not been set");
        return URI.create(toString());
    }

    private ListMultimap<String, String> parseParams(String query)
    {
        LinkedListMultimap<String, String> result = LinkedListMultimap.create();

        if (query != null) {
            for (String pair : QUERY_PARAM_SPLITTER.split(query)) {
                List<String> parts = QUERY_PARAM_VALUE_SPLITTER.splitToList(pair);
                String key = percentDecode(parts.get(0));
                String value = (parts.size() == 2) ? percentDecode(parts.get(1)) : null;
                result.put(key, value);
            }
        }

        return result;
    }

    private static String encode(String input, boolean[] allowed)
    {
        byte[] bytes = input.getBytes(UTF_8);
        int encodedLength = encodedLength(bytes, allowed);
        if (encodedLength == input.length()) {
            return input;
        }
        StringBuilder builder = new StringBuilder(encodedLength);

        for (byte b : bytes) {
            // non-ASCII bytes are negative and always percent-encoded
            if (b >= 0 && allowed[b]) {
                builder.append((char) b); // b is ASCII
            }
            else {
                builder.append('%');
                builder.append(HEX_DIGITS[(b >> 4) & 0xF]);
                builder.append(HEX_DIGITS[b & 0xF]);
            }
        }

        return builder.toString();
    }

    private static int encodedLength(byte[] input, boolean[] allowed)
    {
        int length = input.length;
        for (byte b : input) {
            if (b >= 0 && allowed[b]) {
                continue;
            }
            length += 2; // two extra bytes per encoded octet
        }
        return length;
    }

    /**
     * input must be an ASCII string representing a percent-encoded UTF-8 byte sequence
     */
    private static String percentDecode(String encoded)
    {
        return URLDecoder.decode(encoded, UTF_8);
    }

    private static String bracketedHostString(HostAndPort hostAndPort)
    {
        // HostAndPort cannot return just the bracketed host
        String host = hostAndPort.getHost();
        if (hostAndPort.toString().startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    private static boolean[] buildAllowedTable(byte[] extra)
    {
        boolean[] allowed = new boolean[128];
        for (byte b : PCHAR) {
            allowed[b] = true;
        }
        for (byte c : extra) {
            allowed[c] = true;
        }
        return allowed;
    }
}
