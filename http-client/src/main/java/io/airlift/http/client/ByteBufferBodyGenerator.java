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
package io.airlift.http.client;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

public class ByteBufferBodyGenerator
        implements BodyGenerator
{
    private final ByteBuffer[] byteBuffers;

    public ByteBufferBodyGenerator(ByteBuffer... byteBuffers)
    {
        this.byteBuffers = requireNonNull(byteBuffers, "byteBuffers is null");
    }

    public ByteBuffer[] getByteBuffers()
    {
        return byteBuffers;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void write(OutputStream out)
    {
        throw new UnsupportedOperationException();
    }
}
