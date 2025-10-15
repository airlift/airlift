package io.airlift.security.jwks;

import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

public class JwkRsaPublicKey implements JwkPublicKey, RSAPublicKey {
    private final String keyId;
    private final BigInteger modulus;
    private final BigInteger exponent;

    public JwkRsaPublicKey(String keyId, BigInteger exponent, BigInteger modulus) {
        this.keyId = requireNonNull(keyId, "keyId is null");
        this.exponent = requireNonNull(exponent, "exponent is null");
        this.modulus = requireNonNull(modulus, "modulus is null");
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public BigInteger getModulus() {
        return modulus;
    }

    @Override
    public BigInteger getPublicExponent() {
        return exponent;
    }

    @Override
    public String getAlgorithm() {
        return "RSA";
    }

    @Override
    public String getFormat() {
        return "JWK";
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }
}
