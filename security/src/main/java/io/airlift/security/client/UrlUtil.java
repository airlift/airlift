package io.airlift.security.client;

import com.google.common.base.Throwables;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * url utility functions shared within the package.
 */
class UrlUtil
{
    public static URI normalizedUri(URI uri)
    {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null);
        }
        catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }
}
