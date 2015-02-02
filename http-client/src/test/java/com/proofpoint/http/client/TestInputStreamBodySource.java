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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestInputStreamBodySource
{
    private boolean wasInvoked;

    @BeforeMethod
    public void setup()
    {
        wasInvoked = false;
    }

    @Test
    public void testReadByte()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public int read()
                    throws IOException
            {
                setWasInvoked();
                return 0;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.read();
            }
        };
        assertMethodDelegationConsumes(inputStream, readFromInputStream);
    }

    @Test
    public void testReadByteArray()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public int read(byte[] b)
                    throws IOException
            {
                setWasInvoked();
                return 0;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.read(new byte[1]);
            }
        };
        assertMethodDelegationConsumes(inputStream, readFromInputStream);
    }

    @Test
    public void testReadByteArrayOffsetLength()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public int read(byte[] b, int off, int len)
                    throws IOException
            {
                setWasInvoked();
                return 0;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.read(new byte[1], 0, 1);
            }
        };
        assertMethodDelegationConsumes(inputStream, readFromInputStream);
    }

    @Test
    public void testSkip()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public long skip(long n)
                    throws IOException
            {
                setWasInvoked();
                return 1;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.skip(1);
            }
        };
        assertMethodDelegationConsumes(inputStream, readFromInputStream);
    }

    @Test
    public void testAvailable()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public int available()
                    throws IOException
            {
                setWasInvoked();
                return 1;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.available();
            }
        };
        assertMethodDelegationDoesntConsume(inputStream, readFromInputStream);
    }

    @Test
    public void testClose()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public void close()
                    throws IOException
            {
                setWasInvoked();
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.close();
            }
        };
        assertMethodDelegationConsumes(inputStream, readFromInputStream);
    }

    @Test
    public void testMark()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public void mark(int readlimit)
            {
                setWasInvoked();
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.mark(0);
            }
        };
        assertMethodDelegationDoesntConsume(inputStream, readFromInputStream);
    }

    @Test
    public void testReset()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public void reset()
                    throws IOException
            {
                setWasInvoked();
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.reset();
            }
        };
        assertMethodDelegationDoesntConsume(inputStream, readFromInputStream);
    }

    @Test
    public void testMarkSupported()
            throws IOException
    {
        TestingInputStream inputStream = new TestingInputStream()
        {
            @Override
            public boolean markSupported()
            {
                setWasInvoked();
                return true;
            }
        };
        InputStreamReadFunction readFromInputStream = new InputStreamReadFunction()
        {
            @Override
            public void apply(InputStream inputStream)
                    throws IOException
            {
                inputStream.markSupported();
            }
        };
        assertMethodDelegationDoesntConsume(inputStream, readFromInputStream);
    }

    private void assertMethodDelegationConsumes(TestingInputStream inputStream, InputStreamReadFunction readFromInputStream)
            throws IOException
    {
        InputStreamBodySource bodySource = new InputStreamBodySource(inputStream);
        assertTrue(bodySource.isRetryable());
        readFromInputStream.apply(bodySource.getInputStream());
        assertTrue(wasInvoked);
        assertFalse(bodySource.isRetryable());
        try {
            bodySource.getInputStream();
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "InputStream has been consumed");
        }
    }

    private void assertMethodDelegationDoesntConsume(TestingInputStream inputStream, InputStreamReadFunction readFromInputStream)
            throws IOException
    {
        InputStreamBodySource bodySource = new InputStreamBodySource(inputStream);
        assertTrue(bodySource.isRetryable());
        readFromInputStream.apply(bodySource.getInputStream());
        assertTrue(wasInvoked);
        assertTrue(bodySource.isRetryable());
        wasInvoked = false;
        readFromInputStream.apply(bodySource.getInputStream());
        assertTrue(wasInvoked);
    }

    private class TestingInputStream extends InputStream
    {
        protected void setWasInvoked()
        {
            assertFalse(wasInvoked);
            wasInvoked = true;
        }

        @Override
        public int read()
                throws IOException
        {
            throw new UnsupportedOperationException();
        }
    }

    private abstract class InputStreamReadFunction
    {
        public abstract void apply(InputStream inputStream)
                throws IOException;
    }
}
