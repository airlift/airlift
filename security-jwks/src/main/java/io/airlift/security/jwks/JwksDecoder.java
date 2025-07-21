/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.security.jwks;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Base64.getUrlDecoder;
import static java.util.Objects.requireNonNull;

public final class JwksDecoder
{
    private static final Logger log = Logger.get(JwksDecoder.class);
    private static final JsonCodec<JsonKeys> KEYS_CODEC = JsonCodec.jsonCodec(JsonKeys.class);

    private JwksDecoder() {}

    public static Map<String, PublicKey> decodeKeys(String jwksJson)
    {
        JsonKeys keys = KEYS_CODEC.fromJson(jwksJson);
        return keys.getKeys().stream()
                .map(JwksDecoder::tryDecodeJwkKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toImmutableMap(JwkPublicKey::getKeyId, Function.identity()));
    }

    public static Optional<? extends JwkPublicKey> tryDecodeJwkKey(JsonKey key)
    {
        // key id is required to index the key
        if (key.getKid().isEmpty() || key.getKid().get().isEmpty()) {
            return Optional.empty();
        }
        String keyId = key.getKid().get();
        return switch (key.getKty()) {
            case "RSA" -> tryDecodeRsaKey(keyId, key);
            case "EC" -> tryDecodeEcKey(keyId, key);
            // ignore unknown keys
            default -> Optional.empty();
        };
    }

    public static Optional<JwkRsaPublicKey> tryDecodeRsaKey(String keyId, JsonKey key)
    {
        // alg field is optional so not verified
        // use field is optional so not verified
        Optional<BigInteger> modulus = key.getStringProperty("n").flatMap(encodedModulus -> decodeBigint(keyId, "modulus", encodedModulus));
        if (modulus.isEmpty()) {
            return Optional.empty();
        }

        return key.getStringProperty("e")
                .flatMap(exponent -> decodeBigint(keyId, "exponent", exponent))
                .map(exponent -> new JwkRsaPublicKey(keyId, exponent, modulus.get()));
    }

    public static Optional<JwkEcPublicKey> tryDecodeEcKey(String keyId, JsonKey key)
    {
        // alg field is optional so not verified
        // use field is optional so not verified
        Optional<String> curveName = key.getStringProperty("crv");
        Optional<ECParameterSpec> curve = curveName.flatMap(EcCurve::tryGet);
        if (curve.isEmpty()) {
            log.error("JWK EC %s curve '%s' is not supported", keyId, curveName);
            return Optional.empty();
        }
        Optional<BigInteger> x = key.getStringProperty("x").flatMap(encodedX -> decodeBigint(keyId, "x", encodedX));
        if (x.isEmpty()) {
            return Optional.empty();
        }
        Optional<BigInteger> y = key.getStringProperty("y").flatMap(encodedY -> decodeBigint(keyId, "y", encodedY));
        if (y.isEmpty()) {
            return Optional.empty();
        }

        ECPoint w = new ECPoint(x.get(), y.get());
        return Optional.of(new JwkEcPublicKey(keyId, curve.get(), w));
    }

    private static Optional<BigInteger> decodeBigint(String keyId, String fieldName, String encodedNumber)
    {
        try {
            return Optional.of(new BigInteger(1, getUrlDecoder().decode(encodedNumber)));
        }
        catch (IllegalArgumentException e) {
            log.error(e, "JWK %s %s is not a valid number", keyId, fieldName);
            return Optional.empty();
        }
    }

    public static class JsonKeys
    {
        private final List<JsonKey> keys;

        @JsonCreator
        public JsonKeys(@JsonProperty("keys") List<JsonKey> keys)
        {
            this.keys = ImmutableList.copyOf(requireNonNull(keys, "keys is null"));
        }

        public List<JsonKey> getKeys()
        {
            return keys;
        }
    }

    public static class JsonKey
    {
        private final String kty;
        private final Optional<String> kid;
        private final Map<String, Object> other = new HashMap<>();

        @JsonCreator
        public JsonKey(
                @JsonProperty("kty") String kty,
                @JsonProperty("kid") Optional<String> kid)
        {
            this.kty = requireNonNull(kty, "kty is null");
            this.kid = requireNonNull(kid, "kid is null");
        }

        public String getKty()
        {
            return kty;
        }

        public Optional<String> getKid()
        {
            return kid;
        }

        public Optional<String> getStringProperty(String name)
        {
            Object value = other.get(name);
            if (value instanceof String string && !string.isEmpty()) {
                return Optional.of(string);
            }
            return Optional.empty();
        }

        @JsonAnySetter
        public void set(String name, Object value)
        {
            other.put(name, value);
        }
    }
}
