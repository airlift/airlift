package io.airlift.testing;

import org.testng.annotations.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTempFile
{
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    @Test
    public void testTempFile()
            throws Exception
    {
        TempFile tempFile = new TempFile();
        File file = tempFile.file();

        assertThat(file).isEqualTo(tempFile.path().toFile());
        assertThat(file).exists();
        assertThat(file).isFile();
        assertThat(file).canRead();
        assertThat(file).canWrite();

        tempFile.close();

        assertThat(file).doesNotExist();

        // verify close does not delete file again

        assertThat(file.createNewFile()).isTrue();
        assertThat(file).exists();

        tempFile.close();

        assertThat(file).exists();

        assertThat(file.delete()).isTrue();
    }
}
