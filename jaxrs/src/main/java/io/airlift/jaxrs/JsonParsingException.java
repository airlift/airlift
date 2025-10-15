package io.airlift.jaxrs;

import java.io.IOException;

public class JsonParsingException extends IOException {
    public JsonParsingException(Throwable cause) {
        super(cause);
    }
}
