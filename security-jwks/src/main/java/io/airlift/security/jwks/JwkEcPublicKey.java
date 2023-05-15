package io.airlift.security.jwks;

import java.io.ObjectStreamException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import static java.util.Objects.requireNonNull;

public class JwkEcPublicKey
        implements JwkPublicKey, ECPublicKey
{
    private final String keyId;
    private final ECParameterSpec parameterSpec;
    private final ECPoint w;

    public JwkEcPublicKey(String keyId, ECParameterSpec parameterSpec, ECPoint w)
    {
        this.keyId = requireNonNull(keyId, "keyId is null");
        this.parameterSpec = requireNonNull(parameterSpec, "parameterSpec is null");
        this.w = requireNonNull(w, "w is null");
    }

    @Override
    public String getKeyId()
    {
        return keyId;
    }

    @Override
    public ECParameterSpec getParams()
    {
        return parameterSpec;
    }

    @Override
    public ECPoint getW()
    {
        return w;
    }

    @Override
    public String getAlgorithm()
    {
        return "EC";
    }

    @Override
    public String getFormat()
    {
        return "JWK";
    }

    @Override
    public byte[] getEncoded()
    {
        throw new UnsupportedOperationException();
    }

    protected Object writeReplace()
            throws ObjectStreamException
    {
        throw new UnsupportedOperationException("Java object serialization is not supported");
    }
}
