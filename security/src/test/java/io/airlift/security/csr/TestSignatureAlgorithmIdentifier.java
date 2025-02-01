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

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.junit.jupiter.api.Test;

import java.util.Map.Entry;

import static com.google.common.io.BaseEncoding.base16;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSignatureAlgorithmIdentifier
{
    @Test
    public void test()
            throws Exception
    {
        int verifiedCount = 0;
        for (Entry<String, SignatureAlgorithmIdentifier> entry : SignatureAlgorithmIdentifier.getAllSignatureAlgorithmIdentifiers().entrySet()) {
            SignatureAlgorithmIdentifier signatureAlgorithmIdentifier = entry.getValue();
            assertThat(signatureAlgorithmIdentifier.getName()).isEqualTo(entry.getKey());

            AlgorithmIdentifier algorithmIdentifier;
            try {
                algorithmIdentifier = new DefaultSignatureAlgorithmIdentifierFinder().find(entry.getKey());
            }
            catch (IllegalArgumentException e) {
                // Bouncy is missing some algorithms the JVM supports
                continue;
            }
            assertThat(signatureAlgorithmIdentifier.getOid()).isEqualTo(algorithmIdentifier.getAlgorithm().getId());
            assertThat(base16().encode(signatureAlgorithmIdentifier.getEncoded())).isEqualTo(base16().encode(algorithmIdentifier.getAlgorithm().getEncoded("DER")));
            assertThat(algorithmIdentifier.hashCode()).isEqualTo(algorithmIdentifier.hashCode());
            verifiedCount++;
        }
        assertThat(verifiedCount).as("Algorithm identifiers verified").isGreaterThanOrEqualTo(10);
    }
}
