package io.airlift.testing;

import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestTempFile
{
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    @Test
    public void testTempFile()
            throws Exception
    {
        TempFile tempFile = new TempFile();
        File file = tempFile.file();

        assertEquals(file, tempFile.path().toFile());
        assertTrue(file.exists());
        assertTrue(file.isFile());
        assertTrue(file.canRead());
        assertTrue(file.canWrite());

        tempFile.close();

        assertFalse(file.exists());

        // verify close does not delete file again

        assertTrue(file.createNewFile());
        assertTrue(file.exists());

        tempFile.close();

        assertTrue(file.exists());

        assertTrue(file.delete());
    }
}
