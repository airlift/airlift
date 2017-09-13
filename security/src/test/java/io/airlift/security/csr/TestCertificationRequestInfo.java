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
package io.airlift.security.csr;

import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.testng.annotations.Test;

import javax.security.auth.x500.X500Principal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import static com.google.common.io.BaseEncoding.base16;
import static io.airlift.security.csr.SignatureAlgorithmIdentifier.findSignatureAlgorithmIdentifier;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestCertificationRequestInfo
{
    @Test
    public void test()
            throws Exception
    {
        // test only with state because BC encodes every other value using UTF8String instead of PrintableString used by the JDK
        String name = "C=country";

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        CertificationRequestInfo actualInfo = new CertificationRequestInfo(new X500Principal(name), keyPair.getPublic());
        assertEquals(actualInfo.getPublicKey(), keyPair.getPublic());
        assertEquals(actualInfo.getSubject().getName(), name);
        assertEquals(actualInfo, actualInfo);
        assertEquals(actualInfo.hashCode(), actualInfo.hashCode());

        org.bouncycastle.asn1.pkcs.CertificationRequestInfo expectedInfo = new org.bouncycastle.asn1.pkcs.CertificationRequestInfo(new X500Name(name), SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()), new DERSet());

        assertEquals(
                base16().encode(actualInfo.getEncoded()),
                base16().encode(expectedInfo.getEncoded("DER")));

        SignatureAlgorithmIdentifier signatureAlgorithmIdentifier = findSignatureAlgorithmIdentifier("SHA256withECDSA");
        byte[] actualSignature = actualInfo.sign(signatureAlgorithmIdentifier, keyPair.getPrivate());
        Signature signature = Signature.getInstance(signatureAlgorithmIdentifier.getName());
        signature.initVerify(keyPair.getPublic());
        signature.update(actualInfo.getEncoded());
        assertTrue(signature.verify(actualSignature));
    }
}
