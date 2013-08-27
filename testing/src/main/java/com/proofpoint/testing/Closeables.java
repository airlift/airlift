package com.proofpoint.testing;

import com.google.common.base.Throwables;

import javax.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

public class Closeables
{
    public static void closeQuietly(@Nullable Closeable closeable)
    {
        try {
            if (closeable != null) {
                closeable.close();
            }
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
