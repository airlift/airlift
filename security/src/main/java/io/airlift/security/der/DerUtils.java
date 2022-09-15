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
package io.airlift.security.der;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.ByteStreams.newDataOutput;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * ASN.1 DER encoder methods necessary to process PEM files and to write a certificate signing request.
 * NOTE: this API is only present for the two mentioned use cases, and is subject to change without warning.
 */
public final class DerUtils
{
    private static final DateTimeFormatter UTC_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmssX")
            .withZone(ZoneOffset.UTC);

    public static final int SEQUENCE_TAG = 0x30;
    public static final int BOOLEAN_TAG = 0x01;
    public static final int INTEGER_TAG = 0x02;
    public static final int BIT_STRING_TAG = 0x03;
    public static final int OCTET_STRING_TAG = 0x04;
    public static final int NULL_TAG = 0x05;
    public static final int OBJECT_IDENTIFIER_TAG = 0x06;
    public static final int UTC_TIME_TAG = 0x17;

    private DerUtils() {}

    /**
     * Encodes a sequence of encoded values.
     */
    public static byte[] encodeSequence(byte[]... encodedValues)
    {
        return encodeConstructed(SEQUENCE_TAG, encodedValues);
    }

    /**
     * Decodes a sequence of encoded values.
     */
    public static List<byte[]> decodeSequence(byte[] sequence)
    {
        int index = 0;

        // check tag
        checkArgument(sequence[0] == SEQUENCE_TAG, "Expected sequence tag");
        index++;

        // read length
        int sequenceDataLength = decodeLength(sequence, index);
        index += encodedLengthSize(sequenceDataLength);
        checkArgument(sequenceDataLength + index == sequence.length, "Invalid sequence");

        // read elements
        ImmutableList.Builder<byte[]> elements = ImmutableList.builder();
        while (index < sequence.length) {
            int elementStart = index;

            // skip the tag
            index++;

            // read length
            int length = decodeLength(sequence, index);
            index += encodedLengthSize(length);

            byte[] data = Arrays.copyOfRange(sequence, elementStart, index + length);
            elements.add(data);
            index += length;
        }
        return elements.build();
    }

    /**
     * Decodes a optional element of a sequence.
     */
    public static byte[] decodeSequenceOptionalElement(byte[] element)
    {
        int index = 0;

        // check tag
        checkArgument((element[0] & 0xE0) == 0xA0, "Expected optional sequence element tag");
        index++;

        // read length
        int length = decodeLength(element, index);
        index += encodedLengthSize(length);
        checkArgument(length + index == element.length, "Invalid optional sequence element");

        return Arrays.copyOfRange(element, index, index + length);
    }

    /**
     * Encodes a bit string padded with the specified number of bits.
     * The encoding is a byte containing the padBits followed by the value bytes.
     */
    public static byte[] encodeBitString(int padBits, byte[] value)
    {
        checkArgument(padBits >= 0 && padBits < 8, "Invalid pad bits");

        byte[] lengthEncoded = encodeLength(value.length + 1);
        ByteArrayDataOutput out = newDataOutput(2 + lengthEncoded.length + value.length);
        out.write(BIT_STRING_TAG);
        out.write(lengthEncoded);
        out.write(padBits);
        out.write(value);
        return out.toByteArray();
    }

    /**
     * Encodes an integer.
     */
    public static byte[] encodeBooleanTrue()
    {
        return new byte[] {BOOLEAN_TAG, 0x01, (byte) 0xFF};
    }

    /**
     * Encodes an integer.
     */
    public static byte[] encodeInteger(long value)
    {
        return encodeInteger(BigInteger.valueOf(value));
    }

    /**
     * Encodes an integer.
     */
    public static byte[] encodeInteger(BigInteger value)
    {
        return encodeTag(INTEGER_TAG, value.toByteArray());
    }

    /**
     * Encodes an octet string.
     */
    public static byte[] encodeOctetString(byte[] value)
    {
        return encodeTag(OCTET_STRING_TAG, value);
    }

    /**
     * Encodes an octet string.
     */
    public static byte[] encodeUtcTime(String value)
    {
        return encodeTag(UTC_TIME_TAG, value.getBytes(UTF_8));
    }

    /**
     * Encodes an octet string.
     */
    public static byte[] encodeUtcTime(Instant value)
    {
        String utcTime = UTC_TIME_FORMATTER.format(value);
        return encodeTag(UTC_TIME_TAG, utcTime.getBytes(UTF_8));
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

    private static int encodedLengthSize(int length)
    {
        if (length < 128) {
            return 1;
        }
        int numberOfBits = 32 - Integer.numberOfLeadingZeros(length);
        int numberOfBytes = (numberOfBits + 7) / 8;
        return numberOfBytes + 1;
    }

    static int decodeLength(byte[] buffer, int offset)
    {
        int firstByte = buffer[offset] & 0xFF;
        checkArgument(firstByte != 0x80, "Indefinite lengths not supported in DER");
        checkArgument(firstByte != 0xFF, "Invalid length first byte 0xFF");
        if (firstByte < 128) {
            return firstByte;
        }

        int numberOfBytes = firstByte & 0x7F;
        checkArgument(numberOfBytes <= 4);

        int length = 0;
        for (int i = 0; i < numberOfBytes; i++) {
            length = (length << 8) | (buffer[offset + 1 + i] & 0xFF);
        }
        return length;
    }

    public static byte[] encodeOid(String oid)
    {
        requireNonNull(oid, "oid is null");

        List<Integer> parts = Splitter.on('.').splitToList(oid).stream()
                .map(Integer::parseInt)
                .collect(toImmutableList());
        checkArgument(parts.size() >= 2, "at least 2 parts are required");

        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(parts.get(0) * 40 + parts.get(1));
            for (Integer part : parts.subList(2, parts.size())) {
                writeOidPart(body, part);
            }

            byte[] length = encodeLength(body.size());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(OBJECT_IDENTIFIER_TAG);
            out.write(length);
            body.writeTo(out);
            return out.toByteArray();
        }
        catch (IOException e) {
            // this won't happen with byte array output streams
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Encode an OID number part.  The encoding is a big endian varint.
     */
    private static void writeOidPart(ByteArrayOutputStream out, final int number)
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

    public static byte[] encodeNull()
    {
        return new byte[] {NULL_TAG, 0x00};
    }

    public static byte[] encodeTag(int tag, byte[] body)
    {
        checkArgument(tag >= 0 && tag < 32, "Invalid tag: %s", tag);
        requireNonNull(body, "body is null");
        return encodeTagInternal(tag, body);
    }

    public static byte[] encodeContextSpecificTag(int tag, byte[] body)
    {
        checkArgument(tag >= 0 && tag < 32, "Invalid tag: %s", tag);
        requireNonNull(body, "body is null");
        int privateTag = tag | 0x80;
        return encodeTagInternal(privateTag, body);
    }

    private static byte[] encodeTagInternal(int tag, byte[] body)
    {
        checkArgument(tag >= 0 && tag < 256, "Invalid tag: %s", tag);
        byte[] lengthEncoded = encodeLength(body.length);
        ByteArrayDataOutput out = newDataOutput(1 + lengthEncoded.length + body.length);
        out.write(tag);
        out.write(lengthEncoded);
        out.write(body);
        return out.toByteArray();
    }

    public static byte[] encodeContextSpecificSequence(int tag, byte[]... encodedValues)
    {
        checkArgument(tag >= 0 && tag < 32, "Invalid tag: %s", tag);
        requireNonNull(encodedValues, "body is null");
        int privateTag = tag | 0xA0;

        return encodeConstructed(privateTag, encodedValues);
    }

    private static byte[] encodeConstructed(int privateTag, byte[]... encodedValues)
    {
        int length = 0;
        for (byte[] encodedValue : encodedValues) {
            length += encodedValue.length;
        }
        byte[] lengthEncoded = encodeLength(length);

        ByteArrayDataOutput out = newDataOutput(1 + lengthEncoded.length + length);
        out.write(privateTag);
        out.write(lengthEncoded);
        for (byte[] entry : encodedValues) {
            out.write(entry);
        }
        return out.toByteArray();
    }
}
