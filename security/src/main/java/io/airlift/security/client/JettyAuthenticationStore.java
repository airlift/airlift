package io.airlift.security.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JettyAuthenticationStore implements AuthenticationStore
{
    private static final int DEFAULT_CACHE_SIZE = 10000;
    private static final int DEFAULT_CACHE_EXPIRE_TIME_IN_SECOND = 60 * 5;
    private static final int DEFAULT_CURRENCY_LEVEL = 16;
    private static final String DELIMITER = ":";
    private final Cache<URI, Authentication.Result> results;
    private final Cache<String, Authentication> authentications;

    public JettyAuthenticationStore()
    {
        this(DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EXPIRE_TIME_IN_SECOND);
    }

    public JettyAuthenticationStore(int cacheSize, int cacheExpireTime)
    {
        authentications = CacheBuilder.newBuilder()
                .concurrencyLevel(DEFAULT_CURRENCY_LEVEL)
                .maximumSize(cacheSize)
                .expireAfterAccess(cacheExpireTime, TimeUnit.SECONDS).build();
        results = CacheBuilder.newBuilder()
                .concurrencyLevel(DEFAULT_CURRENCY_LEVEL)
                .maximumSize(cacheSize)
                .expireAfterWrite(cacheExpireTime, TimeUnit.SECONDS).build();
    }

    @Override
    public void addAuthentication(Authentication authentication)
    {
        checkArgument(authentication instanceof JettyAuthentication, "authentication is not instance of JettyAuthentication");
        JettyAuthentication jettyAuthentication = (JettyAuthentication)authentication;
        String key = getKey(jettyAuthentication.getAuthScheme().toString(), jettyAuthentication.getServiceUri());
        authentications.put(key, authentication);
    }

    @Override
    public void removeAuthentication(Authentication authentication)
    {
        checkArgument(authentication instanceof JettyAuthentication, "authentication is not instance of JettyAuthentication");
        JettyAuthentication jettyAuthentication = (JettyAuthentication)authentication;
        String key = getKey(jettyAuthentication.getAuthScheme().toString(), jettyAuthentication.getServiceUri());
        authentications.invalidate(key);
    }

    @Override
    public void clearAuthentications()
    {
        authentications.cleanUp();
    }

    @Override
    public Authentication findAuthentication(String type, URI uri, String realm)
    {
        String key = getKey(type, uri);
        return authentications.getIfPresent(key);
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

    private String getKey(String type, URI uri)
    {
        String s = type + DELIMITER + uri.getScheme() + DELIMITER + uri.getHost();
        return s.toLowerCase();
    }
    @Override
    public void clearAuthenticationResults()
    {
        results.invalidateAll();
    }

    @Override
    public Authentication.Result findAuthenticationResult(URI uri)
    {
        checkNotNull(uri, "uri is null");
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
        return new URI(uri.getScheme(),uri.getHost(), null, null);
    }
}
