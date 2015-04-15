package io.airlift.security.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class ExpiringAuthenticationStore implements AuthenticationStore
{
    private static final int DEFAULT_CACHE_SIZE = 10000;
    private static final Duration DEFAULT_CACHE_EXPIRE_TIME = Duration.ofMinutes(5);
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
    private final Cache<URI, Authentication.Result> results;

    // Authentications are keyed by the AuthScheme.
    private final Map<String, Authentication> authentications;

    public ExpiringAuthenticationStore()
    {
        this(DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EXPIRE_TIME);
    }

    public ExpiringAuthenticationStore(int cacheSize, Duration cacheExpireTime)
    {
        authentications = new HashMap<>();
        results = CacheBuilder.newBuilder()
                .concurrencyLevel(DEFAULT_CONCURRENCY_LEVEL)
                .maximumSize(cacheSize)
                .expireAfterWrite(cacheExpireTime.getSeconds(), TimeUnit.SECONDS).build();
    }

    @Override
    public void addAuthentication(Authentication authentication)
    {
        checkArgument(authentication instanceof JettyAuthentication, "authentication is not instance of JettyAuthentication");
        JettyAuthentication jettyAuthentication = (JettyAuthentication)authentication;
        authentications.put(jettyAuthentication.getAuthScheme().toString(), authentication);
    }

    @Override
    public void removeAuthentication(Authentication authentication)
    {
        checkArgument(authentication instanceof JettyAuthentication,
                "authentication is not instance of JettyAuthentication");
        JettyAuthentication jettyAuthentication = (JettyAuthentication)authentication;
        authentications.remove(jettyAuthentication.getAuthScheme().toString());
    }

    @Override
    public void clearAuthentications()
    {
        authentications.clear();
    }

    @Override
    public Authentication findAuthentication(String type, URI uri, String realm)
    {
        return authentications.get(type);
    }

    @Override
    public void addAuthenticationResult(Authentication.Result result)
    {
        try {
            results.put(normalizedUri(result.getURI()), result);
        } catch (URISyntaxException e) {
            // do nothing
        }
    }

    @Override
    public void removeAuthenticationResult(Authentication.Result result)
    {
        try {
            results.invalidate(normalizedUri(result.getURI()));
        } catch (URISyntaxException e) {
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
            } catch (URISyntaxException e) {
                return null;
            }
        }
        return null;
    }

    private URI normalizedUri(URI uri) throws URISyntaxException
    {
        if (uri == null) {
            return null;
        }
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null);
    }
}
