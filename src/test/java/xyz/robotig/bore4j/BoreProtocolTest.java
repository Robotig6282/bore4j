package xyz.robotig.bore4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoreProtocolTest {
    @Test
    void clientHelloRoundTrip() throws Exception {
        BoreProtocol.Hello hello = new BoreProtocol.Hello(8080);

        JsonNode encoded = BoreProtocol.encodeClient(hello);
        BoreProtocol.ClientMessage decoded = BoreProtocol.decodeClient(encoded);

        assertEquals(hello, decoded);
    }

    @Test
    void decodeServerHeartbeatTextualVariant() throws Exception {
        JsonNode encoded = BoreProtocol.JSON.readTree("\"Heartbeat\"");

        BoreProtocol.ServerMessage decoded = BoreProtocol.decodeServer(encoded);

        assertEquals(BoreProtocol.Heartbeat.INSTANCE, decoded);
    }

    @Test
    void decodeServerConnectionVariant() throws Exception {
        UUID id = UUID.fromString("8164ec10-5840-4f99-8486-a88f0eef223c");
        JsonNode encoded = BoreProtocol.JSON.readTree("{\"Connection\":\"" + id + "\"}");

        BoreProtocol.ServerMessage decoded = BoreProtocol.decodeServer(encoded);

        assertEquals(new BoreProtocol.Connection(id), decoded);
    }

    @Test
    void decodeServerRejectsUnknownVariant() throws Exception {
        JsonNode encoded = BoreProtocol.JSON.readTree("{\"Unknown\":\"x\"}");

        assertThrows(ProtocolException.class, () -> BoreProtocol.decodeServer(encoded));
    }
}
