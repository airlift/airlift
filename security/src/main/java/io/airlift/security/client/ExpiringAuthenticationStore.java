package io.airlift.security.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class ExpiringAuthenticationStore
        implements AuthenticationStore
{
    private static final int CACHE_SIZE = 10000;
    private static final Duration CACHE_EXPIRE_TIME = new Duration(5, TimeUnit.MINUTES);
    private static final int CONCURRENCY_LEVEL = 16;
    private final Cache<URI, Authentication.Result> results;

    // Only support SPNEGO authentication for now
    private final SpnegoAuthentication authentication;

    public ExpiringAuthenticationStore(SpnegoAuthentication authentication)
    {
        this.authentication = authentication;
        results = CacheBuilder.newBuilder()
                .concurrencyLevel(CONCURRENCY_LEVEL)
                .maximumSize(CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_TIME.roundTo(TimeUnit.MINUTES), TimeUnit.MINUTES).build();
    }

    @Override
    public void addAuthentication(Authentication authentication)
    {
        throw new UnsupportedOperationException("addAuthentication is not supported");
    }

    @Override
    public void removeAuthentication(Authentication authentication)
    {
        throw new UnsupportedOperationException("removeAuthentication is not supported");
    }

    @Override
    public void clearAuthentications()
    {
        throw new UnsupportedOperationException("clearAuthentications is not supported");
    }

    @Override
    public Authentication findAuthentication(String type, URI uri, String realm)
    {
        if (authentication.matches(type, uri, realm)) {
            return authentication;
        }
        throw new UnsupportedOperationException(String.format("Authentication type %s is not supported", type));
    }

    @Override
    public void addAuthenticationResult(Authentication.Result result)
    {
        try {
            results.put(normalizedUri(result.getURI()), result);
        }
        catch (URISyntaxException e) {
            // do nothing
        }
    }

    @Override
    public void removeAuthenticationResult(Authentication.Result result)
    {
        try {
            results.invalidate(normalizedUri(result.getURI()));
        }
        catch (URISyntaxException e) {
            // do nothing
        }
    }

    @Override
    public void clearAuthenticationResults()
    {
        results.invalidateAll();
    }

    @Override
    public Authentication.Result findAuthenticationResult(URI uri)
    {
        requireNonNull(uri, "uri is null");
        if (uri.getScheme().equalsIgnoreCase("https")) {
            try {
                // TODO: match the longest URI based on Trie for fine grained control
                return results.getIfPresent(normalizedUri(uri));
            }
            catch (URISyntaxException e) {
                return null;
            }
        }
        return null;
    }

    private URI normalizedUri(URI uri)
            throws URISyntaxException
    {
        if (uri == null) {
            return null;
        }
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null);
    }
}
