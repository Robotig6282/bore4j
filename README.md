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

## Use In Maven Project

Add this dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>xyz.robotig</groupId>
  <artifactId>bore4j</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## Use In Gradle Project

Add this repository and dependency to your `build.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Robotig6282/bore4j")
    }
}

dependencies {
    implementation "xyz.robotig:bore4j:1.0-SNAPSHOT"
}
```

Or in `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.pkg.github.com/Robotig6282/bore4j")
}

dependencies {
    implementation("xyz.robotig:bore4j:1.0-SNAPSHOT")
}
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
