package com.proofpoint.io;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An output stream that writes, first, to a temp file. When completed, the temp
 * file is renamed to the desired destination file. This emulates an atomic write.
 * <p/>
 * Canonical usage:<br/>
<code><pre>
TempFileBackedOutputStream      stream = ...
try
{
    stream.write(...);
    ...
    stream.commit();
}
finally
{
    stream.release();
}
</pre></code>
 */
public interface TempFileBackedOutputStream
{
    /**
     * Returnt the output stream. This can safely be called mulitple times
     *
     * @return stream.
     */
    DataOutputStream        getStream();

    void                    commit() throws IOException;

    void                    release() throws IOException;
}
