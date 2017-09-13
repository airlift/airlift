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
import org.testng.annotations.Test;

import java.util.Map.Entry;

import static com.google.common.io.BaseEncoding.base16;
import static org.testng.Assert.assertEquals;

public class TestSignatureAlgorithmIdentifier
{
    @Test
    public void test()
            throws Exception
    {
        for (Entry<String, SignatureAlgorithmIdentifier> entry : SignatureAlgorithmIdentifier.getAllSignatureAlgorithmIdentifiers().entrySet()) {
            SignatureAlgorithmIdentifier signatureAlgorithmIdentifier = entry.getValue();
            assertEquals(signatureAlgorithmIdentifier.getName(), entry.getKey());

            AlgorithmIdentifier algorithmIdentifier = new DefaultSignatureAlgorithmIdentifierFinder().find(entry.getKey());
            assertEquals(
                    signatureAlgorithmIdentifier.getOid(),
                    algorithmIdentifier.getAlgorithm().getId());
            assertEquals(
                    base16().encode(signatureAlgorithmIdentifier.getEncoded()),
                    base16().encode(algorithmIdentifier.getAlgorithm().getEncoded("DER")));
            assertEquals(algorithmIdentifier, algorithmIdentifier);
            assertEquals(algorithmIdentifier.hashCode(), algorithmIdentifier.hashCode());
        }
    }
}
