package io.airlift.security.mtls;

import com.google.common.annotations.VisibleForTesting;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import java.security.SecureRandomSpi;
import java.security.interfaces.EdECPrivateKey;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Suppliers.memoize;
import static java.security.spec.NamedParameterSpec.ED25519;

final class DeterministicEd25519
{
    private static final int SEED_LENGTH = 32;
    private static final Supplier<Provider> VERIFIED_PROVIDER = memoize(DeterministicEd25519::verifyProvider);

    private DeterministicEd25519() {}

    static KeyPair generateKeyPair(byte[] seed)
            throws GeneralSecurityException
    {
        return generateKeyPair(seed, KeyPairGenerator.getInstance("Ed25519", VERIFIED_PROVIDER.get()));
    }

    private static Provider verifyProvider()
    {
        try {
            // RFC 8032, section 7.1, TEST 1. Check at runtime as well as in tests: a JDK update
            // must fail explicitly rather than silently change the CA identity on some nodes.
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", "SunEC");
            byte[] seed = HexFormat.of().parseHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
            byte[] expectedPublicKey = HexFormat.of().parseHex("302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
            KeyPair keyPair = generateKeyPair(seed, generator);
            if (!Arrays.equals(keyPair.getPublic().getEncoded(), expectedPublicKey)) {
                throw implementationChanged("RFC 8032 public key does not match");
            }
            return generator.getProvider();
        }
        catch (GeneralSecurityException e) {
            throw new ProviderException("Automatic mTLS requires the SunEC Ed25519 key pair generator", e);
        }
    }

    @VisibleForTesting
    static KeyPair generateKeyPair(byte[] seed, KeyPairGenerator generator)
            throws GeneralSecurityException
    {
        checkArgument(seed.length == SEED_LENGTH, "Ed25519 seed must be %s bytes", SEED_LENGTH);
        SeedRandomSpi seedSource = new SeedRandomSpi(seed);
        try {
            generator.initialize(ED25519, new SeedRandom(seedSource));
            KeyPair keyPair = generator.generateKeyPair();
            if (!seedSource.consumed || seedSource.failed) {
                throw implementationChanged("generator did not consume exactly one 32-byte seed");
            }
            if (!(keyPair.getPrivate() instanceof EdECPrivateKey privateKey) || privateKey.getParams() == null || !privateKey.getParams().getName().equals(ED25519.getName())) {
                throw implementationChanged("generator did not return an Ed25519 private key");
            }
            byte[] actualSeed = privateKey.getBytes()
                    .orElseThrow(() -> implementationChanged("private key does not expose its seed"));
            try {
                if (!MessageDigest.isEqual(seed, actualSeed)) {
                    throw implementationChanged("generator transformed the supplied seed");
                }
            }
            finally {
                Arrays.fill(actualSeed, (byte) 0);
            }
            return keyPair;
        }
        finally {
            Arrays.fill(seedSource.seed, (byte) 0);
        }
    }

    private static ProviderException implementationChanged(String detail)
    {
        return new ProviderException("SunEC Ed25519 implementation changed: " + detail);
    }

    private static final class SeedRandom
            extends SecureRandom
    {
        private SeedRandom(SeedRandomSpi seedSource)
        {
            super(seedSource, null);
        }
    }

    // This supplies an RFC 8032 private seed, not a general-purpose random stream. SunEC currently
    // requests exactly 32 bytes and uses them unchanged. Reject other behavior, including additional
    // entropy or reseeding, instead of depending on unspecified provider RNG behavior.
    private static final class SeedRandomSpi
            extends SecureRandomSpi
    {
        private final byte[] seed;
        private boolean consumed;
        private boolean failed;

        private SeedRandomSpi(byte[] seed)
        {
            this.seed = seed.clone();
        }

        @Override
        protected void engineSetSeed(byte[] seed)
        {
            failed = true;
            throw implementationChanged("generator attempted to reseed the seed source");
        }

        @Override
        protected void engineNextBytes(byte[] bytes)
        {
            if (consumed || bytes.length != SEED_LENGTH) {
                failed = true;
                throw implementationChanged("generator requested randomness other than a single 32-byte seed");
            }
            System.arraycopy(seed, 0, bytes, 0, SEED_LENGTH);
            Arrays.fill(seed, (byte) 0);
            consumed = true;
        }

        @Override
        protected byte[] engineGenerateSeed(int length)
        {
            failed = true;
            throw implementationChanged("generator requested additional entropy");
        }

        @Override
        protected void engineNextBytes(byte[] bytes, SecureRandomParameters parameters)
        {
            failed = true;
            throw implementationChanged("generator requested parameterized randomness");
        }

        @Override
        protected void engineReseed(SecureRandomParameters parameters)
        {
            failed = true;
            throw implementationChanged("generator attempted to reseed the seed source");
        }
    }
}
