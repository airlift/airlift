package io.airlift.http.client.jetty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestThrowingLimitingInputStream
{
    @TempDir
    private static Path tempDir;

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasNoLimits(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThat(inputStream.available()).as("initially available").isEqualTo(10);
            assertThat(inputStream.read()).as("first read").isEqualTo('a');
            assertThat(inputStream.read()).as("second read").isEqualTo('b');
            assertThat(inputStream.available()).as("available after two bytes read").isEqualTo(8);
            byte[] buffer = new byte[3];
            assertThat(inputStream.read(buffer)).as("read(byte[3])").isEqualTo(3);
            assertThat(buffer).isEqualTo(new byte[] {'c', 'd', 'e'});
            assertThat(inputStream.available()).as("available after 5 bytes read").isEqualTo(5);
            // read remaining bytes
            buffer = new byte[5];
            assertThat(inputStream.read(buffer)).as("read(byte[5])").isEqualTo(5);
            assertThat(buffer).isEqualTo(new byte[] {'f', 'g', 'h', 'i', 'j'});
            // Stream is exhausted now
            checkStreamAtEof(inputStream, false);
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisNoLimits(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThat(inputStream.read()).as("first read").isEqualTo('a');
            assertThat(inputStream.read()).as("second read").isEqualTo('b');
            byte[] buffer = new byte[3];
            assertThat(inputStream.read(buffer)).as("read(byte[3])").isEqualTo(3);
            assertThat(buffer).isEqualTo(new byte[] {'c', 'd', 'e'});
            assertThat(inputStream.available()).as("available after 5 bytes read").isEqualTo(5);
            // read remaining bytes
            buffer = new byte[5];
            assertThat(inputStream.read(buffer)).as("read(byte[5])").isEqualTo(5);
            assertThat(buffer).isEqualTo(new byte[] {'f', 'g', 'h', 'i', 'j'});
            // Stream is exhausted now
            checkStreamAtEof(inputStream, true);
        }
    }

    @Test
    public void testExceedingLimits()
            throws Exception
    {
        try (InputStream inputStream = new ThrowingLimitingInputStream(new ByteArrayInputStream("abcde".getBytes(ISO_8859_1)), 4)) {
            assertThat(inputStream.read()).as("first #1").isEqualTo('a');
            assertThat(inputStream.read()).as("first #2").isEqualTo('b');
            assertThat(inputStream.read()).as("first #3").isEqualTo('c');
            assertThat(inputStream.read()).as("first #4").isEqualTo('d');
            assertThatThrownBy(inputStream::read)
                    .isInstanceOf(IOException.class)
                    .hasMessage("InputStream exceeded maximum length of 4 [remaining: 0, read: 1]");
            checkStreamExhausted(inputStream);
        }
    }

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasReadPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            byte[] buffer = new byte[13];
            assertThat(inputStream.read(buffer)).as("read(byte[13])").isEqualTo(10);
            assertThat(buffer).isEqualTo((new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 0, 0, 0}));
            checkStreamAtEof(inputStream, false);
        }
    }

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasReadNBytesPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThat(inputStream.readNBytes(13)).as("readNBytes(13)").isEqualTo(new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'});
            checkStreamAtEof(inputStream, false);
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisReadPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            byte[] buffer = new byte[13];
            assertThat(inputStream.read(buffer)).as("read(byte[13])").isEqualTo(10);
            assertThat(buffer).isEqualTo((new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 0, 0, 0}));
            checkStreamAtEof(inputStream, true);
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisReadNBytesPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThat(inputStream.readNBytes(13)).as("readNBytes(13)").isEqualTo(new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'});
            checkStreamAtEof(inputStream, true);
        }
    }

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasSkipPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThat(inputStream.skip(13)).as("skip(13)").isEqualTo(10);
            checkStreamAtEof(inputStream, false);
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisSkipPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            if (inputStream instanceof ThrowingLimitingInputStream) {
                // Some streams (e.g. FileInputStream) allow skipping past EOF, but ThrowingLimitingInputStream
                // does not allow that because, by default, InputStream.skip is implemented by reading and discarding bytes.
                assertThatThrownBy(() -> inputStream.skip(13))
                        .isInstanceOf(IOException.class)
                        .hasMessageStartingWith("InputStream exceeded maximum length of");
            }
            else {
                assertThat(inputStream.skip(13)).as("skip(13)").isEqualTo(13);
                checkStreamAtEof(inputStream, true);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasSkipNBytesPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            assertThatThrownBy(() -> inputStream.skipNBytes(13)).as("skipNBytes(13)")
                    .isInstanceOf(EOFException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisSkipNBytesPastLimit(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            if (inputStream instanceof ThrowingLimitingInputStream) {
                // Some streams (e.g. FileInputStream) allow skipping past EOF, but ThrowingLimitingInputStream
                // does not allow that because, by default, InputStream.skip is implemented by reading and discarding bytes.
                assertThatThrownBy(() -> inputStream.skipNBytes(13))
                        .isInstanceOf(IOException.class)
                        .hasMessageStartingWith("InputStream exceeded maximum length of");
            }
            else {
                inputStream.skipNBytes(13);
                checkStreamAtEof(inputStream, true);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("byteArrayInputStreamOfSize10")
    public void testBiasNegativeSkip(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            byte[] buffer = new byte[7];
            assertThat(inputStream.read(buffer)).as("first read(byte[7])").isEqualTo(7);
            assertThat(buffer).isEqualTo((new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g'}));
            assertThat(inputStream.skip(-5)).as("skip(-5)").isEqualTo(0);
            assertThat(inputStream.read(buffer)).as("second read(byte[7])").isEqualTo(3);
            assertThat(buffer).isEqualTo((new byte[] {'h', 'i', 'j', 'd', 'e', 'f', 'g'}));
        }
    }

    @ParameterizedTest
    @MethodSource("fileInputStreamOfSize10")
    public void testFisNegativeSkip(Supplier<InputStream> open)
            throws Exception
    {
        try (InputStream inputStream = open.get()) {
            byte[] buffer = new byte[7];
            assertThat(inputStream.read(buffer)).as("first read(buffer)").isEqualTo(7);
            assertThat(buffer).isEqualTo((new byte[] {'a', 'b', 'c', 'd', 'e', 'f', 'g'}));
            assertThat(inputStream.skip(-5)).as("first skip(-5)").isEqualTo(-5);
            assertThat(inputStream.read(buffer)).as("second read(buffer)").isEqualTo(7);
            assertThat(buffer).isEqualTo((new byte[] {'c', 'd', 'e', 'f', 'g', 'h', 'i'}));
            assertThat(inputStream.skip(-5)).as("second skip(-5)").isEqualTo(-5);
            assertThat(inputStream.read(buffer)).as("third read(buffer)").isEqualTo(6);
            assertThat(buffer).isEqualTo((new byte[] {'e', 'f', 'g', 'h', 'i', 'j', 'i'}));
        }
    }

    // Testing with and without and without ThrowingLimitingInputStream wrapper verifies
    // how application of the wrapper affects InputStream general contract behavior.
    static List<Supplier<InputStream>> byteArrayInputStreamOfSize10()
    {
        Supplier<byte[]> tenBytes = () -> "abcdefghij".getBytes(ISO_8859_1);
        return List.of(
                named("ByteArrayInputStream", () -> new ByteArrayInputStream(tenBytes.get())),
                named("ThrowingLimitingInputStream 10", () -> new ThrowingLimitingInputStream(new ByteArrayInputStream(tenBytes.get()), 10)),
                named("ThrowingLimitingInputStream 11", () -> new ThrowingLimitingInputStream(new ByteArrayInputStream(tenBytes.get()), 11)));
    }

    // Testing with and without and without ThrowingLimitingInputStream wrapper verifies
    // how application of the wrapper affects InputStream general contract behavior.
    static List<Supplier<InputStream>> fileInputStreamOfSize10()
            throws Exception
    {
        Path filePath = tempDir.resolve("fileInputStreamOfSize10");
        Files.writeString(filePath, "abcdefghij", ISO_8859_1);
        return List.of(
                named("FileInputStream", () -> open(filePath)),
                named("ThrowingLimitingInputStream 10", () -> new ThrowingLimitingInputStream(open(filePath), 10)),
                named("ThrowingLimitingInputStream 11", () -> new ThrowingLimitingInputStream(open(filePath), 11)));
    }

    private static void checkStreamAtEof(InputStream inputStream, boolean baseAllowsSkipPastEnd)
            throws Exception
    {
        assertThat(inputStream.read()).as("read").isEqualTo(-1);
        assertThat(inputStream.available()).as("available").isEqualTo(0);
        byte[] buffer = new byte[3];
        assertThat(inputStream.read(buffer)).as("read(byte[3])").isEqualTo(-1);
        assertThat(inputStream.readNBytes(0)).as("readNBytes(0)").isEqualTo(new byte[0]);
        assertThat(inputStream.readNBytes(2)).as("readNBytes(2)").isEqualTo(new byte[0]);
        assertThat(inputStream.skip(0)).as("skip(0)").isEqualTo(0);
        if (!baseAllowsSkipPastEnd) {
            assertThat(inputStream.skip(1)).as("skip(1)").isEqualTo(0);
            assertThatThrownBy(() -> inputStream.skipNBytes(1)).as("skipNBytes(1)")
                    .isInstanceOf(EOFException.class);
        }
        else if (inputStream instanceof ThrowingLimitingInputStream throwing && throwing.bytesLeft() == 0) {
            assertThatThrownBy(() -> inputStream.skip(1))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("InputStream exceeded maximum length of");
        }
        else {
            assertThat(inputStream.skip(1)).as("skip(1)").isEqualTo(1);
        }
    }

    private static void checkStreamExhausted(InputStream inputStream)
    {
        assertThatThrownBy(inputStream::read).as("read()")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(inputStream::available).as("available()")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.read(new byte[0])).as("read(byte[0])")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.read(new byte[3])).as("read(byte[3])")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.readNBytes(0)).as("readNBytes(0)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.readNBytes(1)).as("readNBytes(1)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.skip(0)).as("skip(0)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.skip(1)).as("skip(1)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.skipNBytes(0)).as("skipNBytes(0)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
        assertThatThrownBy(() -> inputStream.skipNBytes(1)).as("skipNBytes(1)")
                .isInstanceOf(IOException.class)
                .hasMessageMatching("InputStream already exceeded maximum length of \\d+ \\[remaining: -\\d+]");
    }

    private static FileInputStream open(Path file)
    {
        try {
            return new FileInputStream(file.toFile());
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Supplier<T> named(String name, Supplier<T> delegate)
    {
        requireNonNull(name, "name is null");
        requireNonNull(delegate, "delegate is null");
        return new Supplier<T>()
        {
            @Override
            public T get()
            {
                return delegate.get();
            }

            @Override
            public String toString()
            {
                return name;
            }
        };
    }
}
