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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * ASN.1 DER encoder methods necessary to write a certificate request.
 */
final class DerEncoder
{
    private static final int SEQUENCE_TAG = 0x30;
    private static final int BIT_STRING_TAG = 0x03;

    private DerEncoder() {}

    /**
     * Encodes a sequence of encoded values.
     */
    public static byte[] encodeSequence(byte[]... encodedValues)
    {
        int length = 0;
        for (byte[] encodedValue : encodedValues) {
            length += encodedValue.length;
        }
        byte[] lengthEncoded = encodeLength(length);

        ByteArrayDataOutput out = ByteStreams.newDataOutput(1 + lengthEncoded.length + length);
        out.write(SEQUENCE_TAG);
        out.write(lengthEncoded);
        for (byte[] entry : encodedValues) {
            out.write(entry);
        }
        return out.toByteArray();
    }

    /**
     * Encodes a bit string padded with the specified number of bits.
     * The encoding is a byte containing the padBits followed by the value bytes.
     */
    public static byte[] encodeBitString(int padBits, byte[] value)
    {
        checkArgument(padBits >= 0 && padBits < 8, "Invalid pad bits");
        requireNonNull(padBits, "padBits is null");

        byte[] lengthEncoded = encodeLength(value.length + 1);
        ByteArrayDataOutput out = ByteStreams.newDataOutput(2 + lengthEncoded.length + value.length);
        out.write(BIT_STRING_TAG);
        out.write(lengthEncoded);
        out.write(padBits);
        out.write(value);
        return out.toByteArray();
    }

    /**
     * Encodes the length of a DER value.  The encoding of a 7bit value is simply the value.  Values needing more than 7bits
     * are encoded as a lead byte with the high bit set and containing the number of value bytes.  Then the following bytes
     * encode the length using the least number of bytes possible.
     */
    public static byte[] encodeLength(int length)
    {
        if (length < 128) {
            return new byte[] {(byte) length};
        }
        int numberOfBits = 32 - Integer.numberOfLeadingZeros(length);
        int numberOfBytes = (numberOfBits + 7) / 8;
        byte[] encoded = new byte[1 + numberOfBytes];
        encoded[0] = (byte) (numberOfBytes | 0x80);
        for (int i = 0; i < numberOfBytes; i++) {
            int byteToEncode = (numberOfBytes - i);
            int shiftSize = (byteToEncode - 1) * 8;
            encoded[i + 1] = (byte) (length >>> shiftSize);
        }
        return encoded;
    }
}
