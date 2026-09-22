# VoltForge backend

Build and run the backend with **JDK 25 LTS** and Maven 3.9.x. The Maven
compiler uses `--release 25` to target Java 25 language features, APIs, and
bytecode. Configure the IDE project SDK and Maven runner to use JDK 25 too.

## Local build and run

Set `JAVA_HOME` to your JDK 25 installation. For this Windows workstation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
.\mvnw.cmd spring-boot:run
```

Both version commands should report Java 25. Use `.\mvnw.cmd` on Windows, or `./mvnw` on
Linux/macOS.

Supply the service configuration described by `.env.example` through your
environment or deployment launcher. The HTTP port is `2001`, the context path
is `/voltForge-app`, and health is `/voltForge-app/actuator/health`.

## Container build

```sh
docker build -t voltforge-api:java25 .
```

The builder includes Maven and Temurin JDK 25, runs `clean verify` (including
the tests), and packages the application. The final image uses Temurin JRE 25
and runs as the existing non-root `voltforge` user. Rebuild with `--pull` to pick up refreshed base images.
