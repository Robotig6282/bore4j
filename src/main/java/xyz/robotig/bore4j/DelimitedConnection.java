package xyz.robotig.bore4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

final class DelimitedConnection implements AutoCloseable {
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;

    private DelimitedConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    static DelimitedConnection connect(String host, int port, Duration timeout) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), toTimeoutMillis(timeout));
        socket.setTcpNoDelay(true);
        return new DelimitedConnection(socket);
    }

    void sendClient(BoreProtocol.ClientMessage message) throws IOException {
        sendNode(BoreProtocol.encodeClient(message));
    }

    BoreProtocol.ServerMessage receiveServer() throws IOException {
        JsonNode node = receiveNode(null);
        return node == null ? null : BoreProtocol.decodeServer(node);
    }

    BoreProtocol.ServerMessage receiveServer(Duration timeout) throws IOException {
        JsonNode node = receiveNode(timeout);
        return node == null ? null : BoreProtocol.decodeServer(node);
    }

    InputStream rawInputStream() {
        return input;
    }

    OutputStream rawOutputStream() {
        return output;
    }

    Socket socket() {
        return socket;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void sendNode(JsonNode node) throws IOException {
        byte[] payload;
        try {
            payload = BoreProtocol.JSON.writeValueAsBytes(node);
        } catch (JsonProcessingException exception) {
            throw new ProtocolException("unable to serialize message", exception);
        }
        if (payload.length > BoreProtocol.MAX_FRAME_LENGTH) {
            throw new ProtocolException("frame error, invalid byte length");
        }
        output.write(payload);
        output.write(0);
        output.flush();
    }

    private JsonNode receiveNode(Duration timeout) throws IOException {
        int previousTimeout = socket.getSoTimeout();
        if (timeout != null) {
            socket.setSoTimeout(toTimeoutMillis(timeout));
        }
        try {
            byte[] frame = readFrame();
            if (frame == null) {
                return null;
            }
            try {
                return BoreProtocol.JSON.readTree(frame);
            } catch (JsonProcessingException exception) {
                throw new ProtocolException("unable to parse message", exception);
            }
        } finally {
            if (timeout != null) {
                socket.setSoTimeout(previousTimeout);
            }
        }
    }

    private byte[] readFrame() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            int next;
            try {
                next = input.read();
            } catch (SocketTimeoutException timeoutException) {
                throw new ProtocolException("timed out waiting for initial message", timeoutException);
            }
            if (next < 0) {
                if (bytes.size() == 0) {
                    return null;
                }
                throw new EOFException("unexpected EOF while reading frame");
            }
            if (next == 0) {
                return bytes.toByteArray();
            }
            bytes.write(next);
            if (bytes.size() > BoreProtocol.MAX_FRAME_LENGTH) {
                throw new ProtocolException("frame error, invalid byte length");
            }
        }
    }

    private static int toTimeoutMillis(Duration timeout) {
        long millis = timeout.toMillis();
        if (millis <= 0L || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid timeout: " + timeout);
        }
        return (int) millis;
    }
}
