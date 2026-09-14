# VoltForge backend

Build and run the backend with **JDK 21 LTS** and Maven 3.9.x. The Maven
compiler uses `--release 21` to target Java 21 language features, APIs, and
bytecode. Configure the IDE project SDK and Maven runner to use JDK 21 too.

Java 21 is the supported baseline for this project. Between Java 21 and 24,
21 is the better production choice: [Temurin's support roadmap](https://adoptium.net/support)
lists 21 as LTS and Java 24 as end of support since September 2025.

## Local build and run

Set `JAVA_HOME` to your JDK 21 installation. For this Windows workstation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
mvn --batch-mode --no-transfer-progress clean verify
mvn spring-boot:run
```

Both version commands should report Java 21. If the local Maven wrapper is
available, use `.\mvnw.cmd` instead of `mvn` on Windows, or `./mvnw` on
Linux/macOS. Wrapper files are currently excluded from Git, so a fresh clone
needs Maven installed or the Docker build below.

Supply the service configuration described by `.env.example` through your
environment or deployment launcher. The HTTP port is `2001`, the context path
is `/voltForge-app`, and health is `/voltForge-app/actuator/health`.

## Container build

```sh
docker build -t voltforge-api:java21 .
```

The builder includes Maven and Temurin JDK 21, runs `clean verify` (including
the tests), and packages the application. The final image uses Temurin JRE 21
and runs as the existing non-root `voltforge` user. It does not require local
Maven wrapper files. Rebuild with `--pull` to pick up refreshed base images.
