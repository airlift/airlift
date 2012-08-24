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
package io.airlift.launcher;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.POSIXHandler;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class Porting
{
    private static final POSIX posix = POSIXFactory.getPOSIX(new OurPOSIXHandler(), true);

    private Porting()
    {
    }

    static int getpid()
    {
        return posix.getpid();
    }

    private static final class OurPOSIXHandler implements POSIXHandler
    {
        @Override
        public void error(jnr.constants.platform.Errno error, String extraData)
        {
            throw new RuntimeException("native error " + error.description() + " " + extraData);
        }

        @Override
        public void unimplementedError(String methodName)
        {
            throw new IllegalStateException(methodName + " is not implemented in jnr-posix");
        }

        @Override
        public void warn(WARNING_ID id, String message, Object... data)
        {
            String msg;
            try {
                msg = String.format(message, data);
            }
            catch (IllegalFormatException e) {
                msg = message + " " + Arrays.toString(data);
            }
            Logger.getLogger("jnr-posix").log(Level.WARNING, msg);
        }

        @Override
        public boolean isVerbose()
        {
            return false;
        }

        @Override
        public File getCurrentWorkingDirectory()
        {
            return new File(".");
        }

        @Override
        public String[] getEnv()
        {
            String[] envp = new String[System.getenv().size()];
            int i = 0;
            for (Map.Entry<String, String> pair : System.getenv().entrySet()) {
                envp[i++] = pair.getKey() + "=" + pair.getValue();
            }
            return envp;

        }

        @Override
        public InputStream getInputStream()
        {
            return System.in;
        }

        @Override
        public PrintStream getOutputStream()
        {
            return System.out;
        }

        @Override
        public int getPID()
        {
            throw new IllegalStateException("getPID is not implemented in jnr-posix");
        }

        @Override
        public PrintStream getErrorStream()
        {
            return System.err;
        }
    }
}
