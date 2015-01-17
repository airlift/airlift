/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import org.testng.annotations.Test;

import java.io.OutputStream;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestSingleUseBodyGenerator
{
    private static final OutputStream TESTING_OUTPUT_STREAM = new OutputStream()
    {
        @Override
        public void write(int b)
        {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void testWriteOnce()
    {
        SingleUseBodyGenerator generator = spy(new TestingGenerator());
        assertFalse(generator.isUsed());

        generator.write(TESTING_OUTPUT_STREAM);
        assertTrue(generator.isUsed());
        verify(generator).writeOnce(TESTING_OUTPUT_STREAM);
    }

    @Test
    public void testWriteTwice()
    {
        SingleUseBodyGenerator generator = new TestingGenerator();

        generator.write(TESTING_OUTPUT_STREAM);
        try {
            generator.write(TESTING_OUTPUT_STREAM);
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException ignored) {
        }
    }

    private static class TestingGenerator
        extends SingleUseBodyGenerator
    {
        @Override
        protected void writeOnce(OutputStream out)
        {
        }
    }
}
