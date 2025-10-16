package io.airlift.http.client;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

public final class FileBodyGenerator implements BodyGenerator {
    private final Path path;

    public FileBodyGenerator(Path path) {
        this.path = requireNonNull(path, "path is null");
    }

    public Path getPath() {
        return path;
    }
}
