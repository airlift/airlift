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
package io.airlift.log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * To get around the fact that logback closes all appenders, which close their underlying streams,
 * whenever context.reset() is called
 */
class NonCloseableOutputStream
    extends OutputStream
{
    private final OutputStream delegate;

    public NonCloseableOutputStream(OutputStream delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void write(int b)
            throws IOException
    {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b)
            throws IOException
    {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len)
            throws IOException
    {
        delegate.write(b, off, len);
    }

    @Override
    public void flush()
            throws IOException
    {
        delegate.flush();
    }

    @Override
    public void close()
            throws IOException
    {
        // ignore
    }
}
