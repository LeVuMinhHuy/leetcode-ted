# Scripts

## MigrateAddEmail.java

Backfills `email: null` on existing `board_members` documents that have no email field. Safe to run multiple times (idempotent).

### Run

Requires Java 21 and the MongoDB sync driver jars on the classpath.
Build the backend first (`cd backend && gradle dependencies` pulls jars into the Gradle cache), then:

```bash
# From backend/scripts/
DRIVER_VERSION=5.2.1
GRADLE_CACHE=~/.gradle/caches/modules-2/files-2.1

DRIVER_CP=$(find "$GRADLE_CACHE/org.mongodb" -name "*.jar" \
  | grep -E "mongodb-driver-sync|mongodb-driver-core|bson" \
  | tr '\n' ':')

javac -d . -cp "$DRIVER_CP" MigrateAddEmail.java
MONGODB_URI="mongodb+srv://user:pass@host/dbname" java -cp ".:$DRIVER_CP" \
  com.leetcodeted.notification.scripts.MigrateAddEmail
```

> **Note:** The URI must include the database name in its path (e.g. `.../leetcode-ted`).

Output: `Updated N documents` — `N` will be 0 on subsequent runs (idempotent).

## MigrateAddCurrentStreak.java

Backfills `currentStreak: null` on existing `board_members` documents that have no `currentStreak` field. Safe to run multiple times (idempotent).

The actual streak value is populated at runtime by `StreakReminderScheduler` when it runs at 21:00 ICT each day — no manual data entry needed.

### Run

Same classpath setup as above, then:

```bash
# From backend/scripts/
javac -d . -cp "$DRIVER_CP" MigrateAddCurrentStreak.java
MONGODB_URI="mongodb+srv://user:pass@host/dbname" java -cp ".:$DRIVER_CP" \
  com.leetcodeted.notification.scripts.MigrateAddCurrentStreak
```

Output: `Updated N documents` — `N` will be 0 on subsequent runs (idempotent).
