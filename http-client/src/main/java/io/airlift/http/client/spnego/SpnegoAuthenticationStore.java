package io.airlift.http.client.spnego;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class SpnegoAuthenticationStore
        implements AuthenticationStore
{
    private static final int CACHE_SIZE = 10000;
    private static final Duration CACHE_EXPIRE_TIME = new Duration(5, TimeUnit.MINUTES);
    private static final int CONCURRENCY_LEVEL = 16;

    private final Cache<URI, Authentication.Result> results;
    private final SpnegoAuthentication authentication;

    public SpnegoAuthenticationStore(SpnegoAuthentication authentication)
    {
        requireNonNull(authentication, "authentication is null");
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
        return null;
    }

    @Override
    public void addAuthenticationResult(Authentication.Result result)
    {
        results.put(UriUtil.normalizedUri(result.getURI()), result);
    }

    @Override
    public void removeAuthenticationResult(Authentication.Result result)
    {
        results.invalidate(UriUtil.normalizedUri(result.getURI()));
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
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            // TODO: match the longest URI based on Trie for fine grained control
            return results.getIfPresent(UriUtil.normalizedUri(uri));
        }
        return null;
    }
}
