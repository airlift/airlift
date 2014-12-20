/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.jmx.http.rpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;

class HttpMBeanServerRpc
{
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    public static byte[] createSuccessResponse(Object result)
            throws IOException
    {
        return serialize(result);
    }

    public static byte[] createExceptionResponse(Exception exception)
            throws IOException
    {
        return serialize(exception);
    }

    public static byte[] serialize(Object object)
            throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes);
        objectOutputStream.writeObject(object);
        objectOutputStream.flush();
        objectOutputStream.close();
        return bytes.toByteArray();
    }

    public static Object deserialize(InputStream inputStream)
            throws IOException
    {
        try {
            return new ObjectInputStream(inputStream).readObject();
        }
        catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    //
    // This code was swiped from Guava
    //
    public static <X extends Throwable> void propagateIfInstanceOf(Throwable throwable, Class<X> declaredType) throws X {
      if (throwable != null && declaredType.isInstance(throwable)) {
        throw declaredType.cast(throwable);
      }
    }

    public static void propagateIfPossible(Throwable throwable) {
      propagateIfInstanceOf(throwable, Error.class);
      propagateIfInstanceOf(throwable, RuntimeException.class);
    }

    /**
     * Implements the "base64" binary encoding scheme as defined by
     * <a href="http://tools.ietf.org/html/rfc2045">RFC 2045</a>.
     * <p>
     * Portions of code here are taken from Apache Pivot
     */
    private static final char[] lookup = new char[64];
    private static final byte[] reverseLookup = new byte[256];

    static {
        // Populate the lookup array

        for (int i = 0; i < 26; i++) {
            lookup[i] = (char) ('A' + i);
        }

        for (int i = 26, j = 0; i < 52; i++, j++) {
            lookup[i] = (char) ('a' + j);
        }

        for (int i = 52, j = 0; i < 62; i++, j++) {
            lookup[i] = (char) ('0' + j);
        }

        lookup[62] = '+';
        lookup[63] = '/';

        // Populate the reverse lookup array

        for (int i = 0; i < 256; i++) {
            reverseLookup[i] = -1;
        }

        for (int i = 'Z'; i >= 'A'; i--) {
            reverseLookup[i] = (byte) (i - 'A');
        }

        for (int i = 'z'; i >= 'a'; i--) {
            reverseLookup[i] = (byte) (i - 'a' + 26);
        }

        for (int i = '9'; i >= '0'; i--) {
            reverseLookup[i] = (byte) (i - '0' + 52);
        }

        reverseLookup['+'] = 62;
        reverseLookup['/'] = 63;
        reverseLookup['='] = 0;
    }

    public static String base64Encode(String data)
    {
        return base64Encode(data.getBytes(UTF_8));
    }

    /**
     * Encodes the specified data into a base64 string.
     *
     * @param bytes The unencoded raw data.
     */
    public static String base64Encode(byte[] bytes)
    {
        // always sequence of 4 characters for each 3 bytes; padded with '='s as necessary:
        StringBuilder buf = new StringBuilder(((bytes.length + 2) / 3) * 4);

        // first, handle complete chunks (fast loop)
        int i = 0;
        for (int end = bytes.length - 2; i < end; ) {
            int chunk = ((bytes[i++] & 0xFF) << 16)
                    | ((bytes[i++] & 0xFF) << 8)
                    | (bytes[i++] & 0xFF);
            buf.append(lookup[chunk >> 18]);
            buf.append(lookup[(chunk >> 12) & 0x3F]);
            buf.append(lookup[(chunk >> 6) & 0x3F]);
            buf.append(lookup[chunk & 0x3F]);
        }

        // then leftovers, if any
        int len = bytes.length;
        if (i < len) { // 1 or 2 extra bytes?
            int chunk = ((bytes[i++] & 0xFF) << 16);
            buf.append(lookup[chunk >> 18]);
            if (i < len) { // 2 bytes
                chunk |= ((bytes[i] & 0xFF) << 8);
                buf.append(lookup[(chunk >> 12) & 0x3F]);
                buf.append(lookup[(chunk >> 6) & 0x3F]);
            }
            else { // 1 byte
                buf.append(lookup[(chunk >> 12) & 0x3F]);
                buf.append('=');
            }
            buf.append('=');
        }
        return buf.toString();
    }

    /**
     * Decodes the specified base64 string back into its raw data.
     *
     * @param encoded The base64 encoded string.
     */
    public static byte[] base64Decode(String encoded)
    {
        int padding = 0;

        for (int i = encoded.length() - 1; encoded.charAt(i) == '='; i--) {
            padding++;
        }

        int length = encoded.length() * 6 / 8 - padding;
        byte[] bytes = new byte[length];

        for (int i = 0, index = 0, n = encoded.length(); i < n; i += 4) {
            int word = reverseLookup[encoded.charAt(i)] << 18;
            word += reverseLookup[encoded.charAt(i + 1)] << 12;
            word += reverseLookup[encoded.charAt(i + 2)] << 6;
            word += reverseLookup[encoded.charAt(i + 3)];

            for (int j = 0; j < 3 && index + j < length; j++) {
                bytes[index + j] = (byte) (word >> (8 * (2 - j)));
            }

            index += 3;
        }

        return bytes;
    }
}
