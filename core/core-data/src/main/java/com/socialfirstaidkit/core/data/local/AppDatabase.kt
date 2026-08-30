package com.socialfirstaidkit.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.socialfirstaidkit.core.data.local.dao.AppStatsDao
import com.socialfirstaidkit.core.data.local.dao.ContactDao
import com.socialfirstaidkit.core.data.local.dao.MessageCacheDao
import com.socialfirstaidkit.core.data.local.dao.PersonalExpressionDao
import com.socialfirstaidkit.core.data.local.dao.UserProfileDao
import com.socialfirstaidkit.core.data.local.dao.UserStyleDao
import com.socialfirstaidkit.core.data.local.entity.AppStatsEntity
import com.socialfirstaidkit.core.data.local.entity.CachedMessage
import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionFeedbackSampleEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionProfileEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionQuestionnaireDraftEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionUpdateProposalEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionVersionEntity
import com.socialfirstaidkit.core.data.local.entity.UserProfileEntity
import com.socialfirstaidkit.core.data.local.entity.UserStyleEntity

import com.socialfirstaidkit.core.data.local.dao.ChatSessionDao
import com.socialfirstaidkit.core.data.local.dao.ChatMessageDao
import com.socialfirstaidkit.core.data.local.entity.ChatSessionEntity
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity

@Database(
    entities = [
        CachedMessage::class,
        ContactEntity::class,
        UserStyleEntity::class,
        UserProfileEntity::class,
        AppStatsEntity::class,
        PersonalExpressionProfileEntity::class,
        PersonalExpressionQuestionnaireDraftEntity::class,
        PersonalExpressionFeedbackSampleEntity::class,
        PersonalExpressionVersionEntity::class,
        PersonalExpressionUpdateProposalEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageCacheDao(): MessageCacheDao
    abstract fun userStyleDao(): UserStyleDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appStatsDao(): AppStatsDao
    abstract fun personalExpressionDao(): PersonalExpressionDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
}
