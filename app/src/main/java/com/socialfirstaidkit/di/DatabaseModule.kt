package com.socialfirstaidkit.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.socialfirstaidkit.core.data.local.AppDatabase
import com.socialfirstaidkit.core.data.local.dao.AppStatsDao
import com.socialfirstaidkit.core.data.local.dao.ContactDao
import com.socialfirstaidkit.core.data.local.dao.MessageCacheDao
import com.socialfirstaidkit.core.data.local.dao.PersonalExpressionDao
import com.socialfirstaidkit.core.data.local.dao.ChatSessionDao
import com.socialfirstaidkit.core.data.local.dao.ChatMessageDao
import com.socialfirstaidkit.core.data.local.dao.UserProfileDao
import com.socialfirstaidkit.core.data.local.dao.UserStyleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "social_first_aid_db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideMessageCacheDao(database: AppDatabase): MessageCacheDao = database.messageCacheDao()

    @Provides
    fun provideUserStyleDao(database: AppDatabase): UserStyleDao = database.userStyleDao()

    @Provides
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideAppStatsDao(database: AppDatabase): AppStatsDao = database.appStatsDao()

    @Provides
    fun providePersonalExpressionDao(database: AppDatabase): PersonalExpressionDao =
        database.personalExpressionDao()

    @Provides
    fun provideChatSessionDao(database: AppDatabase): ChatSessionDao = database.chatSessionDao()

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao = database.chatMessageDao()

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_expression_profile (
                    id INTEGER NOT NULL PRIMARY KEY,
                    enabled INTEGER NOT NULL,
                    autoLearningEnabled INTEGER NOT NULL,
                    questionnaireCompleted INTEGER NOT NULL,
                    profileJson TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastUpdatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_expression_questionnaire_draft (
                    id INTEGER NOT NULL PRIMARY KEY,
                    answersJson TEXT NOT NULL,
                    currentPage INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_expression_feedback_sample (
                    id TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    style TEXT NOT NULL,
                    scene TEXT NOT NULL,
                    suggestionSlot TEXT NOT NULL,
                    copiedText TEXT NOT NULL,
                    sourceMessage TEXT NOT NULL,
                    profileVersion INTEGER,
                    consumedByProposalId TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_expression_version (
                    id TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL,
                    version INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    profileJson TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_expression_update_proposal (
                    id TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    observationsJson TEXT NOT NULL,
                    changesJson TEXT NOT NULL,
                    previewBefore TEXT NOT NULL,
                    previewAfter TEXT NOT NULL,
                    updatedProfileJson TEXT NOT NULL,
                    sampleIdsJson TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE contacts ADD COLUMN avatarUri TEXT")
            db.execSQL("ALTER TABLE contacts ADD COLUMN defaultRelationId TEXT")
            db.execSQL("ALTER TABLE contacts ADD COLUMN defaultStyleId TEXT")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    sessionId TEXT NOT NULL PRIMARY KEY,
                    contactId TEXT NOT NULL,
                    topicName TEXT NOT NULL,
                    preferredRelationId TEXT,
                    preferredStyleId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(contactId) REFERENCES contacts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_contactId ON chat_sessions(contactId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    messageId TEXT NOT NULL PRIMARY KEY,
                    sessionId TEXT NOT NULL,
                    senderType TEXT NOT NULL,
                    senderName TEXT NOT NULL,
                    content TEXT NOT NULL,
                    messageType TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES chat_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_sessionId_fingerprint ON chat_messages(sessionId, fingerprint)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_chat_messages_fingerprint")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_sessionId_fingerprint ON chat_messages(sessionId, fingerprint)")
        }
    }
}
