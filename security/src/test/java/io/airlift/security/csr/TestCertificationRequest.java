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

import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.testng.annotations.Test;

import javax.security.auth.x500.X500Principal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static com.google.common.io.BaseEncoding.base16;
import static io.airlift.security.csr.SignatureAlgorithmIdentifier.findSignatureAlgorithmIdentifier;
import static org.testng.Assert.assertEquals;

public class TestCertificationRequest
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

        CertificationRequestInfo certificationRequestInfo = new CertificationRequestInfo(new X500Principal(name), keyPair.getPublic());
        SignatureAlgorithmIdentifier signatureAlgorithmIdentifier = findSignatureAlgorithmIdentifier("SHA256withECDSA");
        byte[] signature = certificationRequestInfo.sign(signatureAlgorithmIdentifier, keyPair.getPrivate());

        CertificationRequest certificationRequest = new CertificationRequest(certificationRequestInfo, signatureAlgorithmIdentifier, signature);
        assertEquals(certificationRequest.getCertificationRequestInfo(), certificationRequestInfo);
        assertEquals(certificationRequest.getSignatureAlgorithmIdentifier(), signatureAlgorithmIdentifier);
        assertEquals(base16().encode(certificationRequest.getSignature()), base16().encode(signature));
        assertEquals(certificationRequest, certificationRequest);
        assertEquals(certificationRequest.hashCode(), certificationRequest.hashCode());

        PKCS10CertificationRequest expectedCertificationRequest = new PKCS10CertificationRequest(new org.bouncycastle.asn1.pkcs.CertificationRequest(
                new org.bouncycastle.asn1.pkcs.CertificationRequestInfo(new X500Name(name), SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()), new DERSet()),
                new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withECDSA"),
                new DERBitString(signature)));

        assertEquals(
                base16().encode(certificationRequest.getEncoded()),
                base16().encode(expectedCertificationRequest.getEncoded()));
    }
}
