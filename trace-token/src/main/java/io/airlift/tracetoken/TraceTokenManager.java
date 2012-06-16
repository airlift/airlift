package io.airlift.tracetoken;

import java.util.UUID;

public class TraceTokenManager
{
    private final ThreadLocal<String> token = new ThreadLocal<String>();

    public void registerRequestToken(String token)
    {
        this.token.set(token);
    }

    public String getCurrentRequestToken()
    {
        return this.token.get();
    }

    public String createAndRegisterNewRequestToken()
    {
        String newToken = UUID.randomUUID().toString();
        this.token.set(newToken);

        return newToken;
    }
}
