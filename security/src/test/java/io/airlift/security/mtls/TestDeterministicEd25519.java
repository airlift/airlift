package io.airlift.security.mtls;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HexFormat;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDeterministicEd25519
{
    // RFC 8032, section 7.1, TEST 1 (empty message).
    private static final byte[] SEED = HexFormat.of().parseHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");

    @Test
    public void testRfc8032Vector()
            throws Exception
    {
        byte[] seed = SEED.clone();
        KeyPair keyPair = DeterministicEd25519.generateKeyPair(seed);
        assertThat(seed).isEqualTo(SEED);
        assertThat(((EdECPrivateKey) keyPair.getPrivate()).getBytes().orElseThrow()).isEqualTo(SEED);
        assertThat(HexFormat.of().formatHex(keyPair.getPublic().getEncoded()))
                .isEqualTo("302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");

        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        assertThat(HexFormat.of().formatHex(signature.sign()))
                .isEqualTo("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
    }

    @Test
    public void testUnexpectedSeedLengthIsRejected()
            throws Exception
    {
        for (int length : new int[] {0, 31, 33, 64}) {
            assertProviderChange(random -> random.nextBytes(new byte[length]), "requested randomness other than a single 32-byte seed");
        }
    }

    @Test
    public void testRepeatedSeedRequestIsRejected()
            throws Exception
    {
        assertProviderChange(random -> {
            random.nextBytes(new byte[32]);
            random.nextBytes(new byte[32]);
        }, "requested randomness other than a single 32-byte seed");
    }

    @Test
    public void testIgnoringSeedIsRejected()
            throws Exception
    {
        assertProviderChange(_ -> {}, "did not consume exactly one 32-byte seed");
    }

    @Test
    public void testReseedingIsRejected()
            throws Exception
    {
        assertProviderChange(random -> random.setSeed(new byte[32]), "attempted to reseed");
        assertProviderChange(SecureRandom::reseed, "attempted to reseed");
    }

    @Test
    public void testAdditionalEntropyIsRejected()
            throws Exception
    {
        assertProviderChange(random -> random.generateSeed(32), "requested additional entropy");
    }

    @Test
    public void testSuppressedSeedFailureIsRejected()
            throws Exception
    {
        assertProviderChange(random -> {
            // A changed provider must not be able to swallow the contract failure and continue.
            assertThatThrownBy(() -> random.nextBytes(new byte[64])).isInstanceOf(ProviderException.class);
            random.nextBytes(new byte[32]);
        }, "did not consume exactly one 32-byte seed");
    }

    @Test
    public void testTransformedSeedIsRejected()
            throws Exception
    {
        byte[] otherSeed = SEED.clone();
        otherSeed[0] ^= 1;
        KeyPair otherKeyPair = DeterministicEd25519.generateKeyPair(otherSeed);
        KeyPairGenerator generator = new ChangedKeyPairGenerator(random -> random.nextBytes(new byte[32]), otherKeyPair);

        assertThatThrownBy(() -> DeterministicEd25519.generateKeyPair(SEED, generator))
                .isInstanceOf(ProviderException.class)
                .hasMessage("SunEC Ed25519 implementation changed: generator transformed the supplied seed");
    }

    private static void assertProviderChange(Consumer<SecureRandom> behavior, String message)
            throws Exception
    {
        KeyPairGenerator generator = new ChangedKeyPairGenerator(behavior, DeterministicEd25519.generateKeyPair(SEED));
        assertThatThrownBy(() -> DeterministicEd25519.generateKeyPair(SEED, generator))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("SunEC Ed25519 implementation changed:")
                .hasMessageContaining(message);
    }

    private static final class ChangedKeyPairGenerator
            extends KeyPairGenerator
    {
        private final Consumer<SecureRandom> behavior;
        private final KeyPair result;
        private SecureRandom random;

        private ChangedKeyPairGenerator(Consumer<SecureRandom> behavior, KeyPair result)
        {
            super("Ed25519");
            this.behavior = behavior;
            this.result = result;
        }

        @Override
        public void initialize(AlgorithmParameterSpec params, SecureRandom random)
        {
            this.random = random;
        }

        @Override
        public KeyPair generateKeyPair()
        {
            behavior.accept(random);
            return result;
        }
    }
}
