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
package io.airlift.jaxrs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLimitInputStream
{
    private static final byte[] TEN_BYTES = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

    @Test
    public void testReadWithinLimit()
            throws IOException
    {
        try (LimitInputStream stream = limited(10)) {
            assertThat(stream.readAllBytes()).isEqualTo(TEN_BYTES);
        }
    }

    @Test
    public void testReadOverLimit()
    {
        assertThatThrownBy(() -> limited(9).readAllBytes())
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("Request payload exceeds maximum size of 9 bytes");
    }

    @Test
    public void testSingleByteReadOverLimit()
    {
        assertThatThrownBy(() -> {
            try (LimitInputStream stream = limited(3)) {
                for (int i = 0; i < TEN_BYTES.length; i++) {
                    stream.read();
                }
            }
        }).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    public void testSkipCountsAgainstLimit()
    {
        // skipped bytes are consumed from the payload, so they must not be a way around the limit
        assertThatThrownBy(() -> {
            try (LimitInputStream stream = limited(4)) {
                stream.skip(8);
            }
        }).isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("Request payload exceeds maximum size of 4 bytes");
    }

    @Test
    public void testSkipWithinLimit()
            throws IOException
    {
        try (LimitInputStream stream = limited(10)) {
            assertThat(stream.skip(4)).isEqualTo(4);
            assertThat(stream.readAllBytes()).isEqualTo(new byte[] {4, 5, 6, 7, 8, 9});
        }
    }

    @Test
    public void testResetDoesNotDoubleCount()
            throws IOException
    {
        // re-reading the same bytes after a reset consumes the payload only once
        try (LimitInputStream stream = limited(10)) {
            assertThat(stream.read(new byte[6])).isEqualTo(6);
            stream.mark(10);
            assertThat(stream.read(new byte[4])).isEqualTo(4);
            stream.reset();
            assertThat(stream.readAllBytes()).isEqualTo(new byte[] {6, 7, 8, 9});
        }
    }

    @Test
    public void testResetWithoutMark()
    {
        assertThatThrownBy(() -> limited(10).reset())
                .isInstanceOf(IOException.class)
                .hasMessage("mark not set");
    }

    @Test
    public void testResetWhenMarkUnsupported()
    {
        InputStream noMark = new InputStream()
        {
            @Override
            public int read()
            {
                return 0;
            }

            @Override
            public boolean markSupported()
            {
                return false;
            }
        };

        assertThatThrownBy(() -> new LimitInputStream(noMark, 10).reset())
                .isInstanceOf(IOException.class)
                .hasMessage("mark/reset not supported");
    }

    private static LimitInputStream limited(long maxBytes)
    {
        return new LimitInputStream(new ByteArrayInputStream(TEN_BYTES), maxBytes);
    }
}
