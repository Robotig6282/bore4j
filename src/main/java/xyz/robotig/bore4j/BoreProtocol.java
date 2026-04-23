package xyz.robotig.bore4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class BoreProtocol {
    static final int CONTROL_PORT = 7835;
    static final int MAX_FRAME_LENGTH = 256;
    static final Duration NETWORK_TIMEOUT = Duration.ofSeconds(3);
    static final ObjectMapper JSON = new ObjectMapper();

    private BoreProtocol() {
    }

    sealed interface ClientMessage permits Authenticate, Hello, Accept {
    }

    record Authenticate(String tag) implements ClientMessage {
    }

    record Hello(int port) implements ClientMessage {
    }

    record Accept(UUID id) implements ClientMessage {
    }

    sealed interface ServerMessage permits Challenge, HelloAck, Heartbeat, Connection, Error {
    }

    record Challenge(UUID challenge) implements ServerMessage {
    }

    record HelloAck(int port) implements ServerMessage {
    }

    enum Heartbeat implements ServerMessage {
        INSTANCE
    }

    record Connection(UUID id) implements ServerMessage {
    }

    record Error(String message) implements ServerMessage {
    }

    static JsonNode encodeClient(ClientMessage message) {
        return switch (message) {
            case Authenticate authenticate -> variantObject("Authenticate", JsonNodeFactory.instance.textNode(authenticate.tag()));
            case Hello hello -> variantObject("Hello", JsonNodeFactory.instance.numberNode(hello.port()));
            case Accept accept -> variantObject("Accept", JsonNodeFactory.instance.textNode(accept.id().toString()));
        };
    }

    static ClientMessage decodeClient(JsonNode node) throws ProtocolException {
        if (!node.isObject()) {
            throw new ProtocolException("invalid client message");
        }
        Map.Entry<String, JsonNode> entry = singleEntry(node);
        return switch (entry.getKey()) {
            case "Authenticate" -> new Authenticate(expectText(entry.getValue(), "Authenticate"));
            case "Hello" -> new Hello(expectPort(entry.getValue(), "Hello"));
            case "Accept" -> new Accept(expectUuid(entry.getValue(), "Accept"));
            default -> throw new ProtocolException("unknown client message variant: " + entry.getKey());
        };
    }

    static ServerMessage decodeServer(JsonNode node) throws ProtocolException {
        if (node.isTextual()) {
            if ("Heartbeat".equals(node.asText())) {
                return Heartbeat.INSTANCE;
            }
            throw new ProtocolException("unknown server message variant: " + node.asText());
        }
        if (!node.isObject()) {
            throw new ProtocolException("invalid server message");
        }
        Map.Entry<String, JsonNode> entry = singleEntry(node);
        return switch (entry.getKey()) {
            case "Challenge" -> new Challenge(expectUuid(entry.getValue(), "Challenge"));
            case "Hello" -> new HelloAck(expectPort(entry.getValue(), "Hello"));
            case "Heartbeat" -> Heartbeat.INSTANCE;
            case "Connection" -> new Connection(expectUuid(entry.getValue(), "Connection"));
            case "Error" -> new Error(expectText(entry.getValue(), "Error"));
            default -> throw new ProtocolException("unknown server message variant: " + entry.getKey());
        };
    }

    private static Map.Entry<String, JsonNode> singleEntry(JsonNode node) throws ProtocolException {
        if (node.size() != 1) {
            throw new ProtocolException("message must have exactly one variant");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        return fields.next();
    }

    private static ObjectNode variantObject(String key, JsonNode value) {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.set(key, value);
        return objectNode;
    }

    private static String expectText(JsonNode value, String variant) throws ProtocolException {
        if (!value.isTextual()) {
            throw new ProtocolException("expected string payload for variant " + variant);
        }
        return value.asText();
    }

    private static UUID expectUuid(JsonNode value, String variant) throws ProtocolException {
        String text = expectText(value, variant);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid UUID payload for variant " + variant, exception);
        }
    }

    private static int expectPort(JsonNode value, String variant) throws ProtocolException {
        if (!value.isIntegralNumber()) {
            throw new ProtocolException("expected integer payload for variant " + variant);
        }
        long portValue = value.longValue();
        if (portValue < 0L || portValue > 65535L) {
            throw new ProtocolException("invalid port payload for variant " + variant + ": " + portValue);
        }
        return (int) portValue;
    }
}
