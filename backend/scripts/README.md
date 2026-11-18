# Scripts

## MigrateAddEmail.java

Backfills `email: null` on existing `board_members` documents that have no email field. Safe to run multiple times (idempotent).

### Run

Requires Java 21 and the MongoDB driver jar on the classpath.

```bash
# From repo root — adjust jar path to your Gradle cache
MONGODB_URI="mongodb+srv://..." java -cp backend/build/libs/backend-0.0.1-SNAPSHOT.jar \
  com.leetcodeted.notification.scripts.MigrateAddEmail
```

Alternatively, compile and run directly:
```bash
javac -cp ~/.gradle/caches/modules-2/.../mongodb-driver-sync-5.x.x.jar MigrateAddEmail.java
MONGODB_URI="..." java -cp .:~/.gradle/... MigrateAddEmail
```
