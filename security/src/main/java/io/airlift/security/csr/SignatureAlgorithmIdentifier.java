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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Provider;
import java.security.Security;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.security.csr.DerEncoder.encodeLength;
import static java.util.Objects.requireNonNull;

public final class SignatureAlgorithmIdentifier
{
    private static final int OBJECT_IDENTIFIER_TAG = 0x06;
    private static final Map<String, SignatureAlgorithmIdentifier> ALGORITHMS;

    private static final String SIGNATURE_OID_PREFIX = "Alg.Alias.Signature.OID.";

    static {
        Map<String, SignatureAlgorithmIdentifier> algorithms = new LinkedHashMap<>();
        for (Provider provider : Security.getProviders()) {
            for (Entry<String, String> entry : Maps.fromProperties(provider).entrySet()) {
                if (entry.getKey().startsWith(SIGNATURE_OID_PREFIX)) {
                    String oid = entry.getKey().substring(SIGNATURE_OID_PREFIX.length());
                    SignatureAlgorithmIdentifier algorithmIdentifier = new SignatureAlgorithmIdentifier(entry.getValue(), oid);
                    algorithms.putIfAbsent(entry.getValue(), algorithmIdentifier);
                }
            }
        }
        ALGORITHMS = ImmutableMap.copyOf(algorithms);
    }

    public static Map<String, SignatureAlgorithmIdentifier> getAllSignatureAlgorithmIdentifiers()
    {
        return ALGORITHMS;
    }

    public static SignatureAlgorithmIdentifier findSignatureAlgorithmIdentifier(String algorithmName)
    {
        SignatureAlgorithmIdentifier identifier = ALGORITHMS.get(algorithmName);
        checkArgument(identifier != null, "Unknown signature algorithm '%s'", algorithmName);
        return identifier;
    }

    private final String name;
    private final String oid;
    private final byte[] encoded;

    public SignatureAlgorithmIdentifier(String name, String oid)
    {
        this.name = requireNonNull(name, "name is null");
        this.oid = requireNonNull(oid, "oid is null");

        List<Integer> parts = Splitter.on('.').splitToList(oid).stream()
                .map(Integer::parseInt)
                .collect(toImmutableList());
        checkArgument(parts.size() >= 2, "at least 2 parts are required");

        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(parts.get(0) * 40 + parts.get(1));
            for (Integer part : parts.subList(2, parts.size())) {
                writePart(body, part);
            }

            byte[] length = encodeLength(body.size());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(OBJECT_IDENTIFIER_TAG);
            out.write(length);
            body.writeTo(out);
            encoded = out.toByteArray();
        }
        catch (IOException e) {
            // byte array output stream does not throw exceptions
            throw new RuntimeException(e);
        }
    }

    public String getName()
    {
        return name;
    }

    public String getOid()
    {
        return oid;
    }

    public byte[] getEncoded()
    {
        return encoded.clone();
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
        SignatureAlgorithmIdentifier that = (SignatureAlgorithmIdentifier) o;
        return Objects.equals(oid, that.oid);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(oid);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .add("oid", oid)
                .toString();
    }

    /**
     * Encode an OID number part.  The encoding is a big endian varint.
     */
    private static void writePart(OutputStream out, final int number)
            throws IOException
    {
        if (number < 128) {
            out.write((byte) number);
            return;
        }

        int numberOfBits = Integer.SIZE - Integer.numberOfLeadingZeros(number);
        int numberOfParts = (numberOfBits + 6) / 7;
        for (int i = 0; i < numberOfParts - 1; i++) {
            int partToEncode = (numberOfParts - i);
            int shiftSize = (partToEncode - 1) * 7;
            int part = (number >>> shiftSize) & 0x7F | 0x80;
            out.write(part);
        }
        out.write(number & 0x7f);
    }
}
