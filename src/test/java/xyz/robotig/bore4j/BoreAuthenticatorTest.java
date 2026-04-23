package xyz.robotig.bore4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoreAuthenticatorTest {
    @Test
    void answerMatchesRustVector() {
        BoreAuthenticator authenticator = new BoreAuthenticator("secret");
        UUID challenge = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        String tag = authenticator.answer(challenge);

        assertEquals("5637969ae0c6d11a924980c4b735d78414d6335042c3e4ed857bbe9c12e5595e", tag);
    }
}
