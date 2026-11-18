package com.leetcodeted.notification.scripts;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Backfills {@code currentStreak: null} on existing {@code board_members} documents that have no
 * currentStreak field.
 *
 * <p>Idempotent: documents that already have a {@code currentStreak} field (even if null) are
 * skipped, so running this more than once has no further effect.
 *
 * <p>The actual streak value is populated at runtime by {@code StreakReminderScheduler} when it
 * runs at 21:00 ICT each day.
 *
 * <p>Usage: {@code MONGODB_URI="mongodb+srv://..." java ... MigrateAddCurrentStreak}
 */
public final class MigrateAddCurrentStreak {

    private static final String ENV_MONGODB_URI = "MONGODB_URI";
    private static final String COLLECTION_NAME = "board_members";
    private static final String FIELD = "currentStreak";

    private MigrateAddCurrentStreak() {
    }

    public static void main(String[] args) {
        String uri = System.getenv(ENV_MONGODB_URI);
        if (uri == null || uri.isBlank()) {
            System.err.println("ERROR: environment variable " + ENV_MONGODB_URI + " is not set or empty.");
            System.exit(1);
            return;
        }

        ConnectionString connectionString = new ConnectionString(uri);
        String databaseName = connectionString.getDatabase();
        if (databaseName == null || databaseName.isBlank()) {
            System.err.println("ERROR: " + ENV_MONGODB_URI + " must include a database name in its path.");
            System.exit(1);
            return;
        }

        try (MongoClient client = MongoClients.create(connectionString)) {
            MongoDatabase database = client.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            Bson filter = Filters.exists(FIELD, false);
            Bson update = Updates.set(FIELD, null);

            UpdateResult result = collection.updateMany(filter, update);
            System.out.println("Updated " + result.getModifiedCount() + " documents");
        } catch (RuntimeException e) {
            System.err.println("ERROR: migration failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
