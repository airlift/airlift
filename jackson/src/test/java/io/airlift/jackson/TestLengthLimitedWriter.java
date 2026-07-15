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
package io.airlift.jackson;

import io.airlift.jackson.LengthLimitedWriter.LengthLimitExceededException;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLengthLimitedWriter
{
    @Test
    void testWritesUpToLimitPassThrough()
            throws Exception
    {
        StringWriter delegate = new StringWriter();
        try (LengthLimitedWriter writer = new LengthLimitedWriter(delegate, 5)) {
            writer.write("hello".toCharArray(), 0, 5);
        }
        assertThat(delegate.toString()).isEqualTo("hello");
    }

    @Test
    void testExceedingLimitThrowsAndSuppressesUnderlyingWrite()
    {
        StringWriter delegate = new StringWriter();
        LengthLimitedWriter writer = new LengthLimitedWriter(delegate, 3);
        assertThatThrownBy(() -> writer.write("hello".toCharArray(), 0, 5))
                .isInstanceOf(LengthLimitExceededException.class);
        // the offending chunk must not be forwarded to the delegate
        assertThat(delegate.toString()).isEmpty();
    }

    @Test
    void testLimitIsCumulativeAcrossWrites()
            throws Exception
    {
        StringWriter delegate = new StringWriter();
        LengthLimitedWriter writer = new LengthLimitedWriter(delegate, 4);
        writer.write("ab".toCharArray(), 0, 2);
        assertThatThrownBy(() -> writer.write("cde".toCharArray(), 0, 3))
                .isInstanceOf(LengthLimitExceededException.class);
        assertThat(delegate.toString()).isEqualTo("ab");
    }

    @Test
    void testLengthLimitExceededExceptionIsIoException()
    {
        // must extend IOException so Jackson does not wrap it
        assertThat(new LengthLimitExceededException()).isInstanceOf(java.io.IOException.class);
    }
}
