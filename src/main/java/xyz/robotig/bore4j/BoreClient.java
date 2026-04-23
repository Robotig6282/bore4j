package xyz.robotig.bore4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BoreClient implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(BoreClient.class.getName());
    private static final Duration NETWORK_TIMEOUT = BoreProtocol.NETWORK_TIMEOUT;

    private final DelimitedConnection controlConnection;
    private final String serverHost;
    private final String localHost;
    private final int localPort;
    private final BoreAuthenticator authenticator;
    private final int remotePort;
    private final ExecutorService connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private BoreClient(
            DelimitedConnection controlConnection,
            String serverHost,
            String localHost,
            int localPort,
            int remotePort,
            BoreAuthenticator authenticator
    ) {
        this.controlConnection = controlConnection;
        this.serverHost = serverHost;
        this.localHost = localHost;
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.authenticator = authenticator;
    }

    public static BoreClient connect(
            String localHost,
            int localPort,
            String serverHost,
            int requestedRemotePort,
            String secret
    ) throws IOException {
        return builder()
                .localHost(localHost)
                .localPort(localPort)
                .serverHost(serverHost)
                .remotePort(requestedRemotePort)
                .secret(secret)
                .connect();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int remotePort() {
        return remotePort;
    }

    public void listen() throws IOException {
        try {
            while (!closed.get()) {
                BoreProtocol.ServerMessage message;
                try {
                    message = controlConnection.receiveServer();
                } catch (SocketException exception) {
                    if (closed.get()) {
                        return;
                    }
                    throw exception;
                }
                if (message == null) {
                    return;
                }
                switch (message) {
                    case BoreProtocol.Heartbeat ignored -> {
                    }
                    case BoreProtocol.Connection connection -> submitConnectionTask(connection.id());
                    case BoreProtocol.Error error -> LOG.warning("server error: " + error.message());
                    default -> LOG.warning("unexpected message on control connection: " + message);
                }
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            controlConnection.close();
        } catch (IOException exception) {
            LOG.log(Level.FINE, "error while closing control connection", exception);
        }
        connectionExecutor.shutdownNow();
    }

    private void submitConnectionTask(UUID id) {
        try {
            connectionExecutor.submit(() -> {
                try {
                    handleConnection(id);
                } catch (IOException exception) {
                    LOG.log(Level.WARNING, "connection exited with error for id " + id, exception);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // client is closing
        }
    }

    private void handleConnection(UUID id) throws IOException {
        try (DelimitedConnection remoteConnection =
                     DelimitedConnection.connect(serverHost, BoreProtocol.CONTROL_PORT, NETWORK_TIMEOUT)) {
            if (authenticator != null) {
                authenticator.clientHandshake(remoteConnection);
            }
            remoteConnection.sendClient(new BoreProtocol.Accept(id));
            try (Socket localSocket = connectSocket(localHost, localPort, NETWORK_TIMEOUT)) {
                copyBidirectional(localSocket, remoteConnection);
            }
        }
    }

    private static void copyBidirectional(Socket localSocket, DelimitedConnection remoteConnection) throws IOException {
        InputStream localIn = localSocket.getInputStream();
        OutputStream localOut = localSocket.getOutputStream();
        InputStream remoteIn = remoteConnection.rawInputStream();
        OutputStream remoteOut = remoteConnection.rawOutputStream();

        try (ExecutorService copier = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<IOException> upstream = copier.submit(pipe(localIn, remoteOut, () -> shutdownOutput(remoteConnection.socket())));
            Future<IOException> downstream = copier.submit(pipe(remoteIn, localOut, () -> shutdownOutput(localSocket)));
            IOException upstreamError = awaitCopy(upstream);
            IOException downstreamError = awaitCopy(downstream);
            IOException firstError = upstreamError != null ? upstreamError : downstreamError;
            if (firstError != null) {
                throw firstError;
            }
        }
    }

    private static Callable<IOException> pipe(InputStream input, OutputStream output, Runnable halfClose) {
        return () -> {
            byte[] buffer = new byte[8192];
            try {
                while (true) {
                    int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
                return null;
            } catch (IOException exception) {
                if (isExpectedClose(exception)) {
                    return null;
                }
                return exception;
            } finally {
                halfClose.run();
            }
        };
    }

    private static IOException awaitCopy(Future<IOException> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("copy interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                return ioException;
            }
            throw new IOException("copy failed", cause);
        }
    }

    private static boolean isExpectedClose(IOException exception) {
        if (!(exception instanceof SocketException)) {
            return false;
        }
        String message = exception.getMessage();
        if (message == null) {
            return true;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("socket closed")
                || normalized.contains("broken pipe")
                || normalized.contains("connection reset");
    }

    private static void shutdownOutput(Socket socket) {
        if (socket.isClosed()) {
            return;
        }
        try {
            socket.shutdownOutput();
        } catch (IOException ignored) {
        }
    }

    private static Socket connectSocket(String host, int port, Duration timeout) throws IOException {
        Socket socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(host, port), toTimeoutMillis(timeout));
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static int toTimeoutMillis(Duration timeout) {
        long millis = timeout.toMillis();
        if (millis <= 0L || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid timeout: " + timeout);
        }
        return (int) millis;
    }

    public static final class Builder {
        private String localHost = "localhost";
        private int localPort = -1;
        private String serverHost;
        private int remotePort = 0;
        private String secret;

        public Builder localHost(String localHost) {
            this.localHost = Objects.requireNonNull(localHost, "localHost");
            return this;
        }

        public Builder localPort(int localPort) {
            this.localPort = localPort;
            return this;
        }

        public Builder serverHost(String serverHost) {
            this.serverHost = Objects.requireNonNull(serverHost, "serverHost");
            return this;
        }

        public Builder remotePort(int remotePort) {
            this.remotePort = remotePort;
            return this;
        }

        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        public BoreClient connect() throws IOException {
            validate();
            DelimitedConnection connection = DelimitedConnection.connect(serverHost, BoreProtocol.CONTROL_PORT, NETWORK_TIMEOUT);
            BoreAuthenticator authenticator = (secret == null || secret.isEmpty()) ? null : new BoreAuthenticator(secret);
            try {
                if (authenticator != null) {
                    authenticator.clientHandshake(connection);
                }
                connection.sendClient(new BoreProtocol.Hello(remotePort));
                BoreProtocol.ServerMessage message = connection.receiveServer(NETWORK_TIMEOUT);
                if (message == null) {
                    throw new IOException("unexpected EOF");
                }
                if (message instanceof BoreProtocol.HelloAck hello) {
                    return new BoreClient(connection, serverHost, localHost, localPort, hello.port(), authenticator);
                }
                if (message instanceof BoreProtocol.Error error) {
                    throw new IOException("server error: " + error.message());
                }
                if (message instanceof BoreProtocol.Challenge) {
                    throw new IOException("server requires authentication, but no client secret was provided");
                }
                throw new IOException("unexpected initial non-hello message");
            } catch (IOException exception) {
                connection.close();
                throw exception;
            }
        }

        private void validate() {
            if (serverHost == null || serverHost.isBlank()) {
                throw new IllegalArgumentException("serverHost must be set");
            }
            if (localHost == null || localHost.isBlank()) {
                throw new IllegalArgumentException("localHost must not be blank");
            }
            if (localPort < 1 || localPort > 65535) {
                throw new IllegalArgumentException("localPort must be in range 1..65535");
            }
            if (remotePort < 0 || remotePort > 65535) {
                throw new IllegalArgumentException("remotePort must be in range 0..65535");
            }
        }
    }
}
