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

import com.google.common.io.Resources;
import io.airlift.security.pem.PemReader;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.testng.annotations.Test;

import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static io.airlift.security.jwks.JwksDecoder.decodeKeys;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

public class TestJwksDecoder
{
    @Test
    public void testReadRsaKeys()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "e": "AQAB",
                      "n": "mvj-0waJ2owQlFWrlC06goLs9PcNehIzCF0QrkdsYZJXOsipcHCFlXBsgQIdTdLvlCzNI07jSYA-zggycYi96lfDX-FYv_CqC8dRLf9TBOPvUgCyFMCFNUTC69hsrEYMR_J79Wj0MIOffiVr6eX-AaCG3KhBMZMh15KCdn3uVrl9coQivy7bk2Uw-aUJ_b26C0gWYj1DnpO4UEEKBk1X-lpeUMh0B_XorqWeq0NYK2pN6CoEIh0UrzYKlGfdnMU1pJJCsNxMiha-Vw3qqxez6oytOV_AswlWvQc7TkSX6cHfqepNskQb7pGxpgQpy9sA34oIxB_S-O7VS7_h0Qh4vQ",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA",
                      "kid": "example-rsa"
                    },
                    {
                      "kty": "EC",
                      "use": "sig",
                      "crv": "P-256",
                      "kid": "example-ec",
                      "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                      "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg",
                      "alg": "ES256"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 2);
        assertInstanceOf(keys.get("example-rsa"), JwkRsaPublicKey.class);
        assertInstanceOf(keys.get("example-ec"), JwkEcPublicKey.class);
    }

    @Test
    public void testNoKeyId()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "e": "AQAB",
                      "n": "mvj-0waJ2owQlFWrlC06goLs9PcNehIzCF0QrkdsYZJXOsipcHCFlXBsgQIdTdLvlCzNI07jSYA-zggycYi96lfDX-FYv_CqC8dRLf9TBOPvUgCyFMCFNUTC69hsrEYMR_J79Wj0MIOffiVr6eX-AaCG3KhBMZMh15KCdn3uVrl9coQivy7bk2Uw-aUJ_b26C0gWYj1DnpO4UEEKBk1X-lpeUMh0B_XorqWeq0NYK2pN6CoEIh0UrzYKlGfdnMU1pJJCsNxMiha-Vw3qqxez6oytOV_AswlWvQc7TkSX6cHfqepNskQb7pGxpgQpy9sA34oIxB_S-O7VS7_h0Qh4vQ",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA"
                    },
                    {
                      "kty": "EC",
                      "use": "sig",
                      "crv": "P-256",
                      "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                      "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg",
                      "alg": "ES256"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testRsaNoModulus()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "e": "AQAB",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA",
                      "kid": "2c6fa6f5950a7ce465fcf247aa0b094828ac952c"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testRsaNoExponent()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "n": "mvj-0waJ2owQlFWrlC06goLs9PcNehIzCF0QrkdsYZJXOsipcHCFlXBsgQIdTdLvlCzNI07jSYA-zggycYi96lfDX-FYv_CqC8dRLf9TBOPvUgCyFMCFNUTC69hsrEYMR_J79Wj0MIOffiVr6eX-AaCG3KhBMZMh15KCdn3uVrl9coQivy7bk2Uw-aUJ_b26C0gWYj1DnpO4UEEKBk1X-lpeUMh0B_XorqWeq0NYK2pN6CoEIh0UrzYKlGfdnMU1pJJCsNxMiha-Vw3qqxez6oytOV_AswlWvQc7TkSX6cHfqepNskQb7pGxpgQpy9sA34oIxB_S-O7VS7_h0Qh4vQ",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA",
                      "kid": "2c6fa6f5950a7ce465fcf247aa0b094828ac952c"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testRsaInvalidModulus()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "e": "AQAB",
                      "n": "!!INVALID!!",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA",
                      "kid": "2c6fa6f5950a7ce465fcf247aa0b094828ac952c"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testRsaInvalidExponent()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "e": "!!INVALID!!",
                      "n": "mvj-0waJ2owQlFWrlC06goLs9PcNehIzCF0QrkdsYZJXOsipcHCFlXBsgQIdTdLvlCzNI07jSYA-zggycYi96lfDX-FYv_CqC8dRLf9TBOPvUgCyFMCFNUTC69hsrEYMR_J79Wj0MIOffiVr6eX-AaCG3KhBMZMh15KCdn3uVrl9coQivy7bk2Uw-aUJ_b26C0gWYj1DnpO4UEEKBk1X-lpeUMh0B_XorqWeq0NYK2pN6CoEIh0UrzYKlGfdnMU1pJJCsNxMiha-Vw3qqxez6oytOV_AswlWvQc7TkSX6cHfqepNskQb7pGxpgQpy9sA34oIxB_S-O7VS7_h0Qh4vQ",
                      "alg": "RS256",
                      "use": "sig",
                      "kty": "RSA",
                      "kid": "2c6fa6f5950a7ce465fcf247aa0b094828ac952c"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testJwtRsa()
            throws Exception
    {
        String jwksJson = Resources.toString(Resources.getResource("jwks/jwks-public.json"), UTF_8);
        Map<String, PublicKey> keys = decodeKeys(jwksJson);

        RSAPublicKey publicKey = (RSAPublicKey) keys.get("test-rsa");
        assertNotNull(publicKey);

        RSAPublicKey expectedPublicKey = (RSAPublicKey) PemReader.loadPublicKey(new File(Resources.getResource("jwks/jwk-rsa-public.pem").getPath()));
        assertEquals(publicKey.getPublicExponent(), expectedPublicKey.getPublicExponent());
        assertEquals(publicKey.getModulus(), expectedPublicKey.getModulus());

        PrivateKey privateKey = PemReader.loadPrivateKey(new File(Resources.getResource("jwks/jwk-rsa-private.pem").getPath()), Optional.empty());
        String jwt = Jwts.builder()
                .signWith(privateKey, Jwts.SIG.RS256)
                .header().keyId("test-rsa")
                .and()
                .subject("test-user")
                .expiration(Date.from(ZonedDateTime.now().plusMinutes(5).toInstant()))
                .compact();

        Jws<Claims> claimsJws = Jwts.parser()
                .keyLocator(header -> {
                    String keyId = ((DefaultJwsHeader) header).getKeyId();
                    assertEquals(keyId, "test-rsa");
                    return publicKey;
                })
                .build()
                .parseSignedClaims(jwt);

        assertEquals(claimsJws.getPayload().getSubject(), "test-user");
    }

    @Test
    public void testEcKey()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "kid": "test-ec",
                      "kty": "EC",
                      "crv": "P-256",
                      "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                      "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 1);
        assertInstanceOf(keys.get("test-ec"), JwkEcPublicKey.class);
    }

    @Test
    public void testEcInvalidCurve()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "kid": "test-ec",
                      "kty": "EC",
                      "crv": "taco",
                      "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                      "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testEcInvalidX()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "kid": "test-ec",
                      "kty": "EC",
                      "crv": "P-256",
                      "x": "!!INVALID!!",
                      "y": "XKSNmn_xajgOvWuAiJnWx5I46IwPVJJYPaEpsX3NPZg"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testEcInvalidY()
    {
        Map<String, PublicKey> keys = decodeKeys("""
                {
                  "keys": [
                    {
                      "kid": "test-ec",
                      "kty": "EC",
                      "crv": "P-256",
                      "x": "W9pnAHwUz81LldKjL3BzxO1iHe1Pc0fO6rHkrybVy6Y",
                      "y": "!!INVALID!!"
                    }
                  ]
                }""");
        assertEquals(keys.size(), 0);
    }

    @Test
    public void testJwtEc()
            throws Exception
    {
        assertJwtEc("jwk-ec-p256", Jwts.SIG.ES256, EcCurve.P_256);
        assertJwtEc("jwk-ec-p384", Jwts.SIG.ES384, EcCurve.P_384);
        assertJwtEc("jwk-ec-p512", Jwts.SIG.ES512, EcCurve.P_521);
    }

    private static void assertJwtEc(String keyName, SignatureAlgorithm signatureAlgorithm, ECParameterSpec expectedSpec)
            throws Exception
    {
        String jwksJson = Resources.toString(Resources.getResource("jwks/jwks-public.json"), UTF_8);
        Map<String, PublicKey> keys = decodeKeys(jwksJson);

        ECPublicKey publicKey = (ECPublicKey) keys.get(keyName);
        assertNotNull(publicKey);

        assertSame(publicKey.getParams(), expectedSpec);

        ECPublicKey expectedPublicKey = (ECPublicKey) PemReader.loadPublicKey(new File(Resources.getResource("jwks/" + keyName + "-public.pem").getPath()));
        assertEquals(publicKey.getW(), expectedPublicKey.getW());
        assertEquals(publicKey.getParams().getCurve(), expectedPublicKey.getParams().getCurve());
        assertEquals(publicKey.getParams().getGenerator(), expectedPublicKey.getParams().getGenerator());
        assertEquals(publicKey.getParams().getOrder(), expectedPublicKey.getParams().getOrder());
        assertEquals(publicKey.getParams().getCofactor(), expectedPublicKey.getParams().getCofactor());

        PrivateKey privateKey = PemReader.loadPrivateKey(new File(Resources.getResource("jwks/" + keyName + "-private.pem").getPath()), Optional.empty());
        String jwt = Jwts.builder()
                .signWith(privateKey, signatureAlgorithm)
                .header().keyId(keyName)
                .and()
                .subject("test-user")
                .expiration(Date.from(ZonedDateTime.now().plusMinutes(5).toInstant()))
                .compact();

        Jws<Claims> claimsJws = Jwts.parser()
                .keyLocator(header -> {
                    String keyId = ((DefaultJwsHeader) header).getKeyId();
                    assertEquals(keyId, keyName);
                    String algorithmName = header.getAlgorithm();
                    assertEquals(algorithmName, signatureAlgorithm.getId());
                    return publicKey;
                })
                .build()
                .parseSignedClaims(jwt);

        assertEquals(claimsJws.getPayload().getSubject(), "test-user");
    }
}
