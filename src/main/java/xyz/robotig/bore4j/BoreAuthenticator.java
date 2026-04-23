package xyz.robotig.bore4j;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class BoreAuthenticator {
    private final byte[] hashedSecret;

    BoreAuthenticator(String secret) {
        Objects.requireNonNull(secret, "secret");
        this.hashedSecret = sha256(secret.getBytes(StandardCharsets.UTF_8));
    }

    String answer(UUID challenge) {
        Objects.requireNonNull(challenge, "challenge");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashedSecret, "HmacSHA256"));
            mac.update(uuidBytes(challenge));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to generate auth tag", exception);
        }
    }

    void clientHandshake(DelimitedConnection stream) throws IOException {
        BoreProtocol.ServerMessage message = stream.receiveServer(BoreProtocol.NETWORK_TIMEOUT);
        if (message == null) {
            throw new EOFException("unexpected EOF");
        }
        if (!(message instanceof BoreProtocol.Challenge challenge)) {
            throw new ProtocolException("expected authentication challenge, but no secret was required");
        }
        stream.sendClient(new BoreProtocol.Authenticate(answer(challenge.challenge())));
    }

    private static byte[] uuidBytes(UUID challenge) {
        return ByteBuffer.allocate(16)
                .putLong(challenge.getMostSignificantBits())
                .putLong(challenge.getLeastSignificantBits())
                .array();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
