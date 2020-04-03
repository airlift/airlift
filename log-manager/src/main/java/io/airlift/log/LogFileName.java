/*
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

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.CharMatcher.anyOf;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

final class LogFileName
        implements Comparable<LogFileName>
{
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("-yyyyMMdd.HHmmss");
    private static final int MAX_GENERATED_INDEX = 1000;

    private final String fileName;
    private final LocalDateTime dateTime;
    private final OptionalInt index;
    private final OptionalInt legacyIndex;
    private final Optional<String> slug;
    private final boolean compressed;

    /**
     * Attempts to parse a log file name that is part of the history files for the master log file.
     *
     * New: mainName-yyyyMMdd.HHmmss.compressExtension
     * Legacy: mainName-yyyyMMdd.HHmmss-counter.compressExtension
     *
     * @param masterLogFileName the main file of the logger
     * @param historyFileName the history file name to parse
     * @return a present value if the file name is part of the history set
     */
    public static Optional<LogFileName> parseHistoryLogFileName(String masterLogFileName, String historyFileName)
    {
        requireNonNull(masterLogFileName, "masterLogFileName is null");
        requireNonNull(historyFileName, "historyFileName is null");
        if (!historyFileName.startsWith(masterLogFileName + "-")) {
            return Optional.empty();
        }

        String remainder = historyFileName.substring(masterLogFileName.length() + 1);

        boolean compressed = remainder.endsWith(".gz");
        if (compressed) {
            remainder = remainder.substring(0, remainder.length() - ".gz".length());
        }

        if (remainder.isEmpty()) {
            // This is not considered a valid log file, because the log should have already been renamed
            return Optional.empty();
        }

        if (remainder.endsWith(".log")) {
            return parseLegacyLogName(historyFileName, remainder, compressed);
        }
        return parseNewLogName(historyFileName, remainder, compressed);
    }

    private static Optional<LogFileName> parseNewLogName(String historyFileName, String remainder, boolean compressed)
    {
        // yyyyMMdd.HHmmss-counter
        List<String> parts = Splitter.on(anyOf(".-")).limit(3).splitToList(remainder);
        if (parts.size() < 2) {
            return Optional.empty();
        }

        String date = parts.get(0);
        String time = parts.get(1);
        if (date.length() != 8 || time.length() != 6) {
            return Optional.empty();
        }

        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.of(
                    parseInt(date.substring(0, 4)),
                    parseInt(date.substring(4, 6)),
                    parseInt(date.substring(6, 8)),
                    parseInt(time.substring(0, 2)),
                    parseInt(time.substring(2, 4)),
                    parseInt(time.substring(4, 6)));
        }
        catch (NumberFormatException | DateTimeException e) {
            return Optional.empty();
        }

        int index = 0;
        Optional<String> slug = Optional.empty();
        if (parts.size() == 3) {
            try {
                index = parseInt(parts.get(2));
            }
            catch (NumberFormatException ignored) {
                slug = Optional.of(parts.get(2));
            }
        }
        return Optional.of(new LogFileName(historyFileName, dateTime, OptionalInt.of(index), OptionalInt.empty(), slug, compressed));
    }

    private static Optional<LogFileName> parseLegacyLogName(String historyFileName, String remainder, boolean compressed)
    {
        // %d{yyyy-MM-dd}.%i.log
        remainder = remainder.substring(0, remainder.length() - ".log".length());

        List<String> parts = Splitter.on(anyOf(".-")).limit(4).splitToList(remainder);
        if (parts.size() != 4) {
            return Optional.empty();
        }

        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.of(parseInt(parts.get(0)), parseInt(parts.get(1)), parseInt(parts.get(2)), 0, 0);
        }
        catch (NumberFormatException | DateTimeException e) {
            return Optional.empty();
        }

        int legacyIndex = -1;
        Optional<String> slug = Optional.empty();
        try {
            legacyIndex = parseInt(parts.get(3));
        }
        catch (NumberFormatException ignored) {
            slug = Optional.of(parts.get(3));
        }

        return Optional.of(new LogFileName(historyFileName, dateTime, OptionalInt.empty(), OptionalInt.of(legacyIndex), slug, compressed));
    }

    public static LogFileName generateNextLogFileName(Path masterLogFile, Optional<String> compressionExtension)
    {
        LocalDateTime dateTime = LocalDateTime.now().withNano(0);
        String suffix = DATE_TIME_FORMATTER.format(dateTime);
        for (int index = 0; index < MAX_GENERATED_INDEX; index++) {
            String newFileName = masterLogFile.getFileName() + suffix + (index > 0 ? "-" + index : "");
            Path newFile = masterLogFile.resolveSibling(newFileName);
            if (!fileAlreadyExists(newFile, compressionExtension)) {
                return new LogFileName(newFileName, dateTime, OptionalInt.of(index), OptionalInt.empty(), Optional.empty(), false);
            }
        }
        // something strange is happening, just use a random UUID, so we continue logging
        String slug = randomUUID().toString();
        String randomFileName = masterLogFile.getFileName() + suffix + "--" + slug;
        return new LogFileName(randomFileName, dateTime, OptionalInt.of(0), OptionalInt.empty(), Optional.of(slug), false);
    }

    private static boolean fileAlreadyExists(Path newFile, Optional<String> compressionExtension)
    {
        return Files.exists(newFile) ||
                compressionExtension
                        .map(extension -> newFile.resolveSibling(newFile.getFileName() + extension))
                        .map(Files::exists)
                        .orElse(false);
    }

    private LogFileName(String fileName, LocalDateTime dateTime, OptionalInt index, OptionalInt legacyIndex, Optional<String> slug, boolean compressed)
    {
        this.fileName = requireNonNull(fileName, "fileName is null");
        this.dateTime = requireNonNull(dateTime, "dateTime is null");
        this.index = requireNonNull(index, "index is null");
        this.legacyIndex = requireNonNull(legacyIndex, "legacyIndex is null");
        this.slug = requireNonNull(slug, "slug is null");
        this.compressed = compressed;
    }

    public String getFileName()
    {
        return fileName;
    }

    public LocalDateTime getDateTime()
    {
        return dateTime;
    }

    public OptionalInt getIndex()
    {
        return index;
    }

    public OptionalInt getLegacyIndex()
    {
        return legacyIndex;
    }

    public Optional<String> getSlug()
    {
        return slug;
    }

    public boolean isCompressed()
    {
        return compressed;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogFileName that = (LogFileName) o;
        return dateTime.equals(that.dateTime) &&
                index.equals(that.index) &&
                legacyIndex.equals(that.legacyIndex) &&
                slug.equals(that.slug);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(dateTime, index, legacyIndex, slug);
    }

    @Override
    public int compareTo(LogFileName o)
    {
        return ComparisonChain.start()
                .compare(dateTime, o.dateTime)
                .compareTrueFirst(index.isPresent(), o.index.isPresent())
                .compare(index.orElse(0), o.index.orElse(0))
                .compareTrueFirst(legacyIndex.isPresent(), o.legacyIndex.isPresent())
                .compare(-legacyIndex.orElse(0), -o.legacyIndex.orElse(0))
                .compareFalseFirst(slug.isPresent(), o.slug.isPresent())
                .compare(slug.orElse(""), o.slug.orElse(""))
                .result();
    }

    @Override
    public String toString()
    {
        return fileName;
    }

    public LogFileName withCompression(Path compressedFile)
    {
        return new LogFileName(compressedFile.getFileName().toString(), dateTime, index, legacyIndex, slug, true);
    }
}
