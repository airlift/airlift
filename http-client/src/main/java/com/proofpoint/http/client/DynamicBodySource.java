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

import java.io.OutputStream;

public interface DynamicBodySource extends BodySource
{
    /**
     * Start writing the request body.
     *
     * @param out The @{link OutputStream} to write to
     * @return a @{link Writer} that writes to the indicated @link{OutputStream}.
     *         If the returned value implements @link{AutoCloseable}, the caller
     *         is guaranteed to call @{link AutoCloseable#close()} on it.
     * @throws Exception
     */
    public Writer start(OutputStream out)
            throws Exception;

    public interface Writer
    {
        /**
         * Write some data to the @{link OutputStream} specified when this was
         * created and/or call @{link OutputStream#close()} to indicate the
         * end of the request body.
         * <p>
         * This method will be called repeatedly until either it calls
         * @{link OutputStream#close()} or the caller calls
         * @{link Writer#close()}.
         * <p>
         * For best performance, should not write an excessive amount of data
         * to the @{link OutputStream} in one call.
         *
         * @throws Exception
         */
        public void write()
                throws Exception;
    }
}
