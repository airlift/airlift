package io.airlift.http.client.spnego;

import java.net.URI;
import java.net.URISyntaxException;

class UriUtil
{
    public static URI normalizedUri(URI uri)
    {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
