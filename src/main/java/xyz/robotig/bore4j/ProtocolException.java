package xyz.robotig.bore4j;

import java.io.IOException;

final class ProtocolException extends IOException {
    ProtocolException(String message) {
        super(message);
    }

    ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
