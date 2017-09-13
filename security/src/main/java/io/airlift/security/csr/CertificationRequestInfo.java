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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.security.csr.DerEncoder.encodeSequence;
import static java.util.Objects.requireNonNull;

public class CertificationRequestInfo
{
    private static final byte[] VERSION_0_ENCODED = new byte[] {2, 1, 0};
    private static final byte[] EMPTY_ATTRIBUTES_ENCODED = new byte[] {(byte) 0xA0, 0};

    private final X500Principal subject;
    private final PublicKey publicKey;
    private final byte[] encoded;

    public CertificationRequestInfo(X500Principal subject, PublicKey publicKey)
    {
        this.subject = requireNonNull(subject, "subject is null");
        this.publicKey = requireNonNull(publicKey, "publicKey is null");
        this.encoded = encodeSequence(VERSION_0_ENCODED, subject.getEncoded(), publicKey.getEncoded(), EMPTY_ATTRIBUTES_ENCODED);
    }

    public X500Principal getSubject()
    {
        return subject;
    }

    public PublicKey getPublicKey()
    {
        return publicKey;
    }

    public byte[] getEncoded()
    {
        return encoded.clone();
    }

    public byte[] sign(SignatureAlgorithmIdentifier signatureAlgorithmIdentifier, PrivateKey privateKey)
            throws GeneralSecurityException
    {
        Signature signature = Signature.getInstance(signatureAlgorithmIdentifier.getName());
        signature.initSign(privateKey);
        signature.update(getEncoded());
        return signature.sign();
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
        CertificationRequestInfo that = (CertificationRequestInfo) o;
        return Objects.equals(subject, that.subject) &&
                Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(subject, publicKey);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("subject", subject)
                .add("publicKey", publicKey)
                .toString();
    }
}
