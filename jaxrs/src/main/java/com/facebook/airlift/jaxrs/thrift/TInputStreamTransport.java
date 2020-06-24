/*
 * Copyright (C) 2013 Facebook, Inc.
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
package com.facebook.airlift.jaxrs.thrift;

import com.facebook.drift.protocol.TTransport;
import com.facebook.drift.protocol.TTransportException;

import javax.annotation.concurrent.NotThreadSafe;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

@NotThreadSafe
public class TInputStreamTransport
        implements TTransport
{
    private final InputStream inputStream;

    public TInputStreamTransport(InputStream inputStream)
    {
        this.inputStream = requireNonNull(inputStream, "inputStream is null");
    }

    @Override
    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        try {
            inputStream.read(buf, off, len);
        }
        catch (IOException e) {
            throw new TTransportException(e);
        }
    }

    @Override
    public void write(byte[] buf, int off, int len)
    {
        throw new UnsupportedOperationException();
    }
}
