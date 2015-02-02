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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

public class InputStreamBodySource
    implements BodySource, LimitedRetryable
{
    private final InputStream inputStream;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    public InputStreamBodySource(final InputStream inputStream)
    {
        this.inputStream = new InputStream()
        {
            public boolean firstCall = true;

            @Override
            public int read()
                    throws IOException
            {
                setConsumed();
                return inputStream.read();
            }

            @Override
            public int read(byte[] b)
                    throws IOException
            {
                setConsumed();
                return inputStream.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len)
                    throws IOException
            {
                setConsumed();
                return inputStream.read(b, off, len);
            }

            @Override
            public long skip(long n)
                    throws IOException
            {
                setConsumed();
                return inputStream.skip(n);
            }

            @Override
            public int available()
                    throws IOException
            {
                return inputStream.available();
            }

            @Override
            public void close()
                    throws IOException
            {
                setConsumed();
                inputStream.close();
            }

            @Override
            public void mark(int readlimit)
            {
                inputStream.mark(readlimit);
            }

            @Override
            public void reset()
                    throws IOException
            {
                inputStream.reset();
            }

            @Override
            public boolean markSupported()
            {
                return inputStream.markSupported();
            }

            private void setConsumed()
            {
                if (firstCall) {
                    firstCall = false;
                    consumed.set(true);
                }
            }
        };
    }

    public InputStream getInputStream()
    {
        checkState(!consumed.get(), "InputStream has been consumed");
        return inputStream;
    }

    @Override
    public boolean isRetryable()
    {
        return !consumed.get();
    }
}
