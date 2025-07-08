package com.example.tareamov.data

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tareamov.data.dao.PersonaDao
import com.example.tareamov.data.dao.UsuarioDao
import com.example.tareamov.data.dao.VideoDao
import com.example.tareamov.data.dao.TopicDao
import com.example.tareamov.data.dao.ContentItemDao
import com.example.tareamov.data.dao.TaskDao
import com.example.tareamov.data.dao.SubscriptionDao
import com.example.tareamov.data.dao.TaskSubmissionDao
import com.example.tareamov.data.dao.PurchaseDao
import com.example.tareamov.data.dao.ChatMessageDao
import com.example.tareamov.data.dao.FileContextDao
import com.example.tareamov.data.entity.Persona
import com.example.tareamov.data.entity.Usuario
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.data.entity.Topic
import com.example.tareamov.data.entity.ContentItem
import com.example.tareamov.data.entity.Task
import com.example.tareamov.data.entity.Subscription
import com.example.tareamov.data.entity.TaskSubmission
import com.example.tareamov.data.entity.Purchase
import com.example.tareamov.data.entity.ChatMessage
import com.example.tareamov.data.entity.FileContext
import com.example.tareamov.service.OllamaService
import com.example.tareamov.service.MSPClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.tareamov.service.DatabaseContextHttpServer

// In the @Database annotation, add Purchase to the entities list
@Database(
    entities = [
        Persona::class,
        Usuario::class,
        VideoData::class,
        Topic::class,
        ContentItem::class,
        Task::class,
        Subscription::class,
        TaskSubmission::class,
        Purchase::class,
        ChatMessage::class,
        FileContext::class  // Add FileContext entity here
    ],
    version = 22, // <-- Update version from 21 to 22
    exportSchema = false
)
@TypeConverters(VideoDataConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun personaDao(): PersonaDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun topicDao(): TopicDao
    abstract fun contentItemDao(): ContentItemDao
    abstract fun taskDao(): TaskDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun taskSubmissionDao(): TaskSubmissionDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun fileContextDao(): FileContextDao  // Add this line

    // Métodos para notificar cambios en la base de datos
    fun notifyDataChanged() {
        databaseQueryService?.updateDatabaseJson()
    }

    // Referencia al servicio para actualización dinámica del JSON
    @Volatile
    private var databaseQueryService: com.example.tareamov.service.DatabaseQueryService? = null

    fun setDatabaseQueryService(service: com.example.tareamov.service.DatabaseQueryService) {
        databaseQueryService = service
    }

    fun notifyDatabaseChanged() {
        databaseQueryService?.let {
            it.updateDatabaseJson()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var httpServer: DatabaseContextHttpServer? = null // Add this line

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20 // <-- Add new migration
                    )
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                INSTANCE = instance

                // Start the HTTP server for database context
                if (httpServer == null) {
                    httpServer = DatabaseContextHttpServer(context, instance) // Pass context here
                    httpServer?.start()
                }

                // Start the Ollama service when the database is initialized
                startOllamaService(context)

                // Also initialize the local Llama model
                initializeLocalLlamaModel(context)

                instance
            }
        }

        private fun initializeLocalLlamaModel(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val mspClient = MSPClient(context)
                    mspClient.preloadLocalModel()
                    Log.d(TAG, "Started local Llama 3 model initialization")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize local Llama 3 model", e)
                }
            }
        }

        private fun startOllamaService(context: Context) {
            try {
                Log.d(TAG, "Starting OllamaService")
                val intent = Intent(context, OllamaService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting OllamaService", e)
            }
        }

        // Define migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the esUsuario column to the personas table
                db.execSQL("ALTER TABLE personas ADD COLUMN esUsuario INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Define migration from version 2 to 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the videos table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS videos (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "username TEXT NOT NULL, " +
                            "description TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "videoUriString TEXT, " +
                            "timestamp INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // Define migration from version 3 to 4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add localFilePath column to videos table
                db.execSQL("ALTER TABLE videos ADD COLUMN localFilePath TEXT")
            }
        }

        // Define migration from version 4 to 5 (adding Topic and ContentItem tables)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create topics table with proper foreign key
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `topics` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `videos`(`id`) ON DELETE CASCADE
                )"""
                )

                // Create index on courseId in topics table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_courseId` ON `topics` (`courseId`)")

                // Create content_items table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `content_items` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`topicId` INTEGER NOT NULL, "
                            + "`name` TEXT NOT NULL, "
                            + "`uriString` TEXT NOT NULL, "
                            + "`contentType` TEXT NOT NULL, "
                            + "`orderIndex` INTEGER NOT NULL, "
                            + "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )

                // Create index on topicId in content_items table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_topicId` ON `content_items` (`topicId`)")
            }
        }

        // Define migration from version 5 to 6 (adding Task table)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create tasks table - Corrected FK and Index
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `topicId` INTEGER NOT NULL, -- Corrected from courseId
                    `name` TEXT NOT NULL,
                    `description` TEXT, -- Made nullable in previous step
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON DELETE CASCADE -- Corrected reference from videos(id)
                )"""
                )

                // Create index on topicId in tasks table - Corrected index name and column
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_topicId` ON `tasks` (`topicId`)")
            }
        }

        // Define migration from version 6 to 7 (Empty migration for now, add SQL if schema changed)
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add necessary SQL statements here if the schema actually changed between version 6 and 7.
                // For example: db.execSQL("ALTER TABLE your_table ADD COLUMN new_column TEXT")
                Log.i(TAG, "Executing migration from 6 to 7")
            }
        }

        // Define migration from version 7 to 8 (Empty migration to force schema update)
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed, but this forces Room to re-check the schema.
                Log.i(TAG, "Executing migration from 7 to 8")
            }
        }

        // Define migration from version 8 to 9 (Fix tasks table schema)
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the existing tasks table if it exists
                db.execSQL("DROP TABLE IF EXISTS `tasks`")

                // Recreate the tasks table with the correct schema
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `topicId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON DELETE CASCADE
                )"""
                )

                // Create index on topicId in tasks table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_topicId` ON `tasks` (`topicId`)")

                Log.i(TAG, "Executing migration from 8 to 9: Fixed tasks table schema")
            }
        }

        // Define migration from version 9 to 10 (Fix foreign key constraints)
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Enable foreign key support
                db.execSQL("PRAGMA foreign_keys = ON")

                // Create a temporary table for topics without foreign key constraints
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `topics_temp` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL
                )"""
                )

                // Copy data from topics to topics_temp
                db.execSQL("INSERT OR IGNORE INTO topics_temp SELECT * FROM topics")

                // Drop the original topics table
                db.execSQL("DROP TABLE IF EXISTS topics")

                // Recreate the topics table with proper foreign key constraints
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `topics` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `videos`(`id`) ON DELETE CASCADE
                )"""
                )

                // Create index on courseId in topics table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_courseId` ON `topics` (`courseId`)")

                // Copy data back from topics_temp to topics
                db.execSQL("INSERT OR IGNORE INTO topics SELECT * FROM topics_temp")

                // Drop the temporary table
                db.execSQL("DROP TABLE IF EXISTS topics_temp")

                // Create a default course if none exists
                db.execSQL(
                    """INSERT OR IGNORE INTO videos (id, username, description, title, timestamp)
                   SELECT 1, 'default_user', 'Curso predeterminado', 'Curso predeterminado', ${System.currentTimeMillis()}
                   WHERE NOT EXISTS (SELECT 1 FROM videos LIMIT 1)"""
                )

                Log.i(TAG, "Executing migration from 9 to 10: Fixed foreign key constraints")
            }
        }

        // Define migration from version 10 to 11 (Add Subscription table)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `subscriptions` (
                    `subscriberUsername` TEXT NOT NULL,
                    `creatorUsername` TEXT NOT NULL,
                    `subscriptionDate` INTEGER NOT NULL,
                    PRIMARY KEY(`subscriberUsername`, `creatorUsername`)
                )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_subscriberUsername` ON `subscriptions` (`subscriberUsername`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_creatorUsername` ON `subscriptions` (`creatorUsername`)")
            }
        }

        // Update the MIGRATION_11_12 to properly handle the subscriptions table
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the indices that are causing the mismatch
                db.execSQL("DROP INDEX IF EXISTS index_subscriptions_creatorUsername")
                db.execSQL("DROP INDEX IF EXISTS index_subscriptions_subscriberUsername")

                // Create a temporary table with the correct structure
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `subscriptions_temp` (
                    `subscriberUsername` TEXT NOT NULL,
                    `creatorUsername` TEXT NOT NULL,
                    `subscriptionDate` INTEGER NOT NULL,
                    PRIMARY KEY(`subscriberUsername`, `creatorUsername`)
                )"""
                )

                // Copy data from the old table to the new one
                db.execSQL("INSERT OR IGNORE INTO subscriptions_temp SELECT subscriberUsername, creatorUsername, subscriptionDate FROM subscriptions")

                // Drop the old table
                db.execSQL("DROP TABLE subscriptions")

                // Rename the new table to the original name
                db.execSQL("ALTER TABLE subscriptions_temp RENAME TO subscriptions")

                // Create the indices that match the entity definition
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_subscriberUsername` ON `subscriptions` (`subscriberUsername`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_creatorUsername` ON `subscriptions` (`creatorUsername`)")

                Log.i(TAG, "Migration 11 to 12 completed: Fixed subscriptions table schema")
            }
        }

        // Add migration from version 12 to 13
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure indices exist for subscriptions table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_subscriberUsername` ON `subscriptions` (`subscriberUsername`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_creatorUsername` ON `subscriptions` (`creatorUsername`)")

                Log.i(TAG, "Executing migration from 12 to 13: Ensuring subscription indices exist")
            }
        }

        // Add migration from version 13 to 14
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure indices exist for subscriptions table (from original migration)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_subscriberUsername` ON `subscriptions` (`subscriberUsername`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_creatorUsername` ON `subscriptions` (`creatorUsername`)")

                // --- Start of changes for content_items ---
                // Recreate content_items table to match the expected schema (add taskId, FK, index, adjust nullability)
                // SQLite requires table recreation to add foreign keys or modify constraints significantly.

                // a. Create a temporary table with the new structure matching the ContentItem entity
                db.execSQL("""
                    CREATE TABLE `content_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `name` TEXT, -- Adjusted to nullable based on 'Expected' schema in error
                        `uriString` TEXT NOT NULL,
                        `contentType` TEXT NOT NULL,
                        `orderIndex` INTEGER, -- Adjusted to nullable based on 'Expected' schema in error
                        `taskId` INTEGER, -- New column based on 'Expected' schema
                        FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON DELETE CASCADE -- New FK based on 'Expected' schema
                    )
                """)

                // b. Copy data from the old table to the new table.
                //    Select specific columns to avoid issues if the old table schema differs.
                //    Set the new taskId column to NULL for existing rows.
                db.execSQL("""
                    INSERT INTO `content_items_new` (`id`, `topicId`, `name`, `uriString`, `contentType`, `orderIndex`, `taskId`)
                    SELECT `id`, `topicId`, `name`, `uriString`, `contentType`, `orderIndex`, NULL
                    FROM `content_items`
                """)

                // c. Drop the old content_items table
                db.execSQL("DROP TABLE `content_items`")

                // d. Rename the new temporary table to the original name
                db.execSQL("ALTER TABLE `content_items_new` RENAME TO `content_items`")

                // e. Recreate indices for the new table structure
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_topicId` ON `content_items` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_taskId` ON `content_items` (`taskId`)") // New index for taskId

                // --- End of changes for content_items ---

                Log.i(TAG, "Migration 13 to 14: Ensured subscription indices exist and updated content_items schema.")
            }
        }

        // Add migration from version 14 to 15 (Add rol column to usuarios table)
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the rol column to the usuarios table with default value "usuario"
                db.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT NOT NULL DEFAULT 'usuario'")

                Log.i(TAG, "Migration 14 to 15: Added rol column to usuarios table")
            }
        }
    }
}

// Add migration from version 15 to 16 (Add TaskSubmission table)
private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create TaskSubmission table
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `task_submissions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `taskId` INTEGER NOT NULL,
            `studentUsername` TEXT NOT NULL,
            `submissionDate` INTEGER NOT NULL,
            `fileUri` TEXT NOT NULL,
            `fileName` TEXT NOT NULL,
            `grade` REAL,
            `feedback` TEXT,
            FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON DELETE CASCADE
        )"""
        )

        // Create indices for task_submissions table
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_submissions_taskId` ON `task_submissions` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_submissions_studentUsername` ON `task_submissions` (`studentUsername`)")

        Log.i(TAG, "Migration 15 to 16: Added task_submissions table")
    }
}

// Define migration from version 16 to 17 (Update user roles)
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Update any existing "usuario" roles to "estudiante"
        db.execSQL("UPDATE usuarios SET rol = 'estudiante' WHERE rol = 'usuario'")
        Log.i(TAG, "Migration 16 to 17 completed: Updated user roles from 'usuario' to 'estudiante'")
    }
}

// Add this migration at the end of your migrations list
// Define migration from version 17 to 18 (Add Purchase table)
private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create purchases table
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `purchases` (
            `username` TEXT NOT NULL,
            `courseId` INTEGER NOT NULL,
            `purchaseDate` INTEGER NOT NULL,
            PRIMARY KEY(`username`, `courseId`)
        )"""
        )

        // Create index on courseId in purchases table
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_courseId` ON `purchases` (`courseId`)")

        // Add isPaid column to videos table if it doesn't exist
        try {
            db.execSQL("ALTER TABLE videos ADD COLUMN isPaid INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding isPaid column to videos table: ${e.message}")
            // Column might already exist, which is fine
        }

        Log.i(TAG, "Migration 17 to 18 completed: Added purchases table and isPaid column")
    }
}

// Add this migration at the end of your migrations list
// Define migration from version 18 to 19 (Add thumbnailUri column to videos table)
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE videos ADD COLUMN thumbnailUri TEXT")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding thumbnailUri column to videos table: ${e.message}")
        }
        Log.i(TAG, "Migration 18 to 19 completed: Added thumbnailUri column to videos table")
    }
}

// Add this migration at the end of your migrations list
// Add this migration at the end of your migrations list
private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            // Add price column to purchases table
            db.execSQL("ALTER TABLE purchases ADD COLUMN price REAL")

            // Add price column to videos table
            db.execSQL("ALTER TABLE videos ADD COLUMN price REAL")

            Log.i(TAG, "Migration 19 to 20 completed: Added price column to purchases and videos tables")
        } catch (e: Exception) {
            Log.e(TAG, "Error in migration 19 to 20: ${e.message}")
        }
    }
}