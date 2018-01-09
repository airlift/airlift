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

import javax.security.auth.x500.X500Principal;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.io.BaseEncoding.base16;
import static io.airlift.security.csr.DerEncoder.encodeBitString;
import static io.airlift.security.csr.DerEncoder.encodeSequence;
import static io.airlift.security.csr.SignatureAlgorithmIdentifier.findSignatureAlgorithmIdentifier;
import static java.util.Base64.getMimeEncoder;
import static java.util.Objects.requireNonNull;

public class CertificationRequest
{
    private final CertificationRequestInfo certificationRequestInfo;
    private final SignatureAlgorithmIdentifier signatureAlgorithmIdentifier;
    private final byte[] signature;
    private final byte[] encoded;

    public CertificationRequest(String x500Name, String signatureAlgorithm, KeyPair keyPair)
            throws GeneralSecurityException
    {
        this(new CertificationRequestInfo(new X500Principal(x500Name), keyPair.getPublic()), findSignatureAlgorithmIdentifier(signatureAlgorithm), keyPair.getPrivate());
    }

    public CertificationRequest(CertificationRequestInfo certificationRequestInfo, SignatureAlgorithmIdentifier signatureAlgorithmIdentifier, PrivateKey privateKey)
            throws GeneralSecurityException
    {
        this(certificationRequestInfo, signatureAlgorithmIdentifier, certificationRequestInfo.sign(signatureAlgorithmIdentifier, privateKey));
    }

    public CertificationRequest(CertificationRequestInfo certificationRequestInfo, SignatureAlgorithmIdentifier signatureAlgorithmIdentifier, byte[] signature)
    {
        this.certificationRequestInfo = requireNonNull(certificationRequestInfo, "certificationRequestInfo is null");
        this.signatureAlgorithmIdentifier = requireNonNull(signatureAlgorithmIdentifier, "signatureAlgorithmIdentifier is null");
        this.signature = requireNonNull(signature, "signature is null").clone();

        this.encoded = encodeSequence(
                certificationRequestInfo.getEncoded(),
                encodeSequence(signatureAlgorithmIdentifier.getEncoded()),
                encodeBitString(0, signature));
    }

    public CertificationRequestInfo getCertificationRequestInfo()
    {
        return certificationRequestInfo;
    }

    public SignatureAlgorithmIdentifier getSignatureAlgorithmIdentifier()
    {
        return signatureAlgorithmIdentifier;
    }

    public byte[] getSignature()
    {
        return signature.clone();
    }

    public byte[] getEncoded()
    {
        return encoded.clone();
    }

    public String getPemEncoded()
    {
        return "-----BEGIN CERTIFICATE REQUEST-----\n" +
                getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded) + '\n' +
                "-----END CERTIFICATE REQUEST-----\n";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CertificationRequest that = (CertificationRequest) o;
        return Objects.equals(certificationRequestInfo, that.certificationRequestInfo) &&
                Objects.equals(signatureAlgorithmIdentifier, that.signatureAlgorithmIdentifier) &&
                Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(certificationRequestInfo, signatureAlgorithmIdentifier, signature);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("certificationRequestInfo", certificationRequestInfo)
                .add("signatureAlgorithmIdentifier", signatureAlgorithmIdentifier)
                .add("signature", base16().encode(signature))
                .toString();
    }
}
