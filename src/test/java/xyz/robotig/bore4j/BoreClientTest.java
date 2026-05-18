package xyz.robotig.bore4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoreClientTest {
    @Test
    void serverErrorMessageExplainsPortConflictForFixedRemotePort() {
        String message = BoreClient.serverErrorMessage("port already in use", 25565);

        assertEquals(
                "server error: port already in use (requested remote port 25565). Choose another remote port or use 0 for automatic assignment.",
                message
        );
    }

    @Test
    void serverErrorMessageKeepsGenericFormatWhenRemotePortIsDynamic() {
        String message = BoreClient.serverErrorMessage("port already in use", 0);

        assertEquals("server error: port already in use", message);
    }
}
