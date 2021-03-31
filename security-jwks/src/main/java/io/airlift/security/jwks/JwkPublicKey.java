package io.airlift.security.jwks;

import java.security.PublicKey;

public interface JwkPublicKey
        extends PublicKey
{
    String getKeyId();
}
