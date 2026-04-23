# bore4j

Java client library for the [bore](https://github.com/ekzhang/bore) TCP tunneling protocol.

This project implements bore client-side control protocol in Java 21:

## Requirements

- Java 21+
- Maven 3.9+

## Build

```bash
mvn compile
```

## Test

```bash
mvn test
```

## Quick Start

```java
import xyz.robotig.bore4j.BoreClient;

public class Main {
    public static void main(String[] args) throws Exception {
        BoreClient client = BoreClient.builder()
                .localHost("localhost")
                .localPort(8000)
                .serverHost("bore.pub")
                .remotePort(0)     // 0 = random remote port
                .secret(null)      // set shared secret if server requires auth
                .connect();

        System.out.println("Public remote port: " + client.remotePort());
        client.listen(); // blocks, handles incoming tunnel connections
    }
}
```