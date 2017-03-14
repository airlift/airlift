package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Bytes;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Iterator;
import java.util.Map;

import static com.google.common.base.CharMatcher.ascii;
import static java.lang.Character.forDigit;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * An RFC-3986-compatible HTTP URI builder
 */
@Beta
public class HttpUriBuilder
{
    private String scheme;
    private String host;
    private int port = -1;
    private String path = ""; // decoded path
    private final ListMultimap<String, String> params = LinkedListMultimap.create(); // decoded query params

    private static final byte[] PCHAR = {
            'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
            'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
            '0','1','2','3','4','5','6','7','8','9',
            '-', '.', '_', '~', '!', '$', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '@',
    };

    private static final byte[] ALLOWED_PATH_CHARS = Bytes.concat(PCHAR, new byte[] {'/', '&'});
    private static final byte[] ALLOWED_QUERY_CHARS = Bytes.concat(PCHAR, new byte[] {'/', '?'});

    private HttpUriBuilder()
    {
    }

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
        Preconditions.checkArgument(!host.startsWith("["), "host starts with a bracket");
        Preconditions.checkArgument(!host.endsWith("]"), "host ends with a bracket");
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        this.host = host;
        return this;
    }

    public HttpUriBuilder port(int port)
    {
        Preconditions.checkArgument(port >= 1 && port <= 65535, "port must be in the range 1-65535");
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

        if (!path.equals("") && !path.startsWith("/")) {
            path = "/" + path;
        }

        this.path = path;
        return this;
    }

    /**
     * Append an unencoded path.
     *
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

        if (Iterables.isEmpty(values)) {
            params.put(name, null);
        }

        for (String value : values) {
            params.put(name, value);
        }

        return this;
    }

    // return an RFC-3986-compatible URI
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(scheme);
        builder.append("://");
        if (host != null) {
            builder.append(host);
        }
        if (port != -1) {
            builder.append(':');
            builder.append(port);
        }

        String path = this.path;
        if (path.equals("") && !params.isEmpty()) {
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

    public URI build()
    {
        Preconditions.checkState(scheme != null, "scheme has not been set");
        return URI.create(toString());
    }

    private ListMultimap<String, String> parseParams(String query)
    {
        LinkedListMultimap<String, String> result = LinkedListMultimap.create();

        if (query != null) {
            Iterable<String> pairs = Splitter.on("&")
                    .omitEmptyStrings()
                    .split(query);

            for (String pair : pairs) {
                String[] parts = pair.split("=", 2);
                result.put(percentDecode(parts[0]), percentDecode(parts[1]));
            }
        }

        return result;
    }

    private String encode(String input, byte... allowed)
    {
        StringBuilder builder = new StringBuilder();

        ByteBuffer buffer = UTF_8.encode(input);
        while (buffer.remaining() > 0) {
            byte b = buffer.get();

            if (Bytes.contains(allowed, b)) {
                builder.append((char) b); // b is ASCII
            }
            else {
                builder.append('%');
                builder.append(Ascii.toUpperCase(forDigit((b >>> 4) & 0xF, 16)));
                builder.append(Ascii.toUpperCase(forDigit(b & 0xF, 16)));
            }
        }

        return builder.toString();
    }

    /**
     * input must be an ASCII string representing a percent-encoded UTF-8 byte sequence
     */
    private static String percentDecode(String encoded)
    {
        Preconditions.checkArgument(ascii().matchesAllOf(encoded), "string must be ASCII");

        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);

            if (c == '%') {
                Preconditions.checkArgument(i + 2 < encoded.length(), "percent encoded value is truncated");

                int high = Character.digit(encoded.charAt(i + 1), 16);
                int low = Character.digit(encoded.charAt(i + 2), 16);

                Preconditions.checkArgument(high != -1 && low != -1, "percent encoded value is not a valid hex string: ", encoded.substring(i, i + 2));

                int value = (high << 4) | (low);
                out.write(value);
                i += 2;
            }
            else {
                out.write((int) c);
            }
        }

        try {
            return UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray()))
                    .toString();
        }
        catch (CharacterCodingException e) {
            throw new IllegalArgumentException("input does not represent a proper UTF8-encoded string");
        }
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
}
