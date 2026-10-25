package io.airlift.http.client.jetty;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSegmentedInputStream
{
    private static final byte[] FIRST = {1, 2, 3};
    private static final byte[] SECOND = {4, 5};
    private static final byte[] THIRD = {6, 7, 8, 9, 0, 0};
    // The last segment is only partially filled
    private static final ResponseBody BODY = new ResponseBody(List.of(FIRST, SECOND, THIRD), 9);

    @Test
    public void testReadAllBytes()
    {
        assertThat(new SegmentedInputStream(BODY).readAllBytes()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    public void testSingleByteReads()
    {
        SegmentedInputStream stream = new SegmentedInputStream(BODY);
        for (int expected = 1; expected <= 9; expected++) {
            assertThat(stream.read()).isEqualTo(expected);
        }
        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    public void testBulkReadsAcrossSegments()
    {
        SegmentedInputStream stream = new SegmentedInputStream(BODY);
        byte[] destination = new byte[4];
        assertThat(stream.read(destination, 0, 4)).isEqualTo(4);
        assertThat(destination).containsExactly(1, 2, 3, 4);
        assertThat(stream.available()).isEqualTo(5);
        assertThat(stream.skip(2)).isEqualTo(2);
        assertThat(stream.readAllBytes()).containsExactly(7, 8, 9);
        assertThat(stream.read(destination, 0, 4)).isEqualTo(-1);
        assertThat(stream.read(destination, 0, 0)).isEqualTo(0);
    }

    @Test
    public void testReadNBytesAndTransferTo()
            throws IOException
    {
        SegmentedInputStream stream = new SegmentedInputStream(BODY);
        assertThat(stream.readNBytes(4)).containsExactly(1, 2, 3, 4);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(stream.transferTo(output)).isEqualTo(5);
        assertThat(output.toByteArray()).containsExactly(5, 6, 7, 8, 9);
        assertThat(stream.readNBytes(4)).isEmpty();
    }

    @Test
    public void testEmptyBody()
    {
        SegmentedInputStream stream = new SegmentedInputStream(ResponseBody.EMPTY);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.readAllBytes()).isEmpty();
    }
}
