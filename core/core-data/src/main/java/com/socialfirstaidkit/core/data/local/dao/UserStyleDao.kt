package com.socialfirstaidkit.core.data.local.dao

import androidx.room.*
import com.socialfirstaidkit.core.data.local.entity.UserStyleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStyleDao {
    @Query("SELECT * FROM user_style ORDER BY frequency DESC LIMIT 50")
    fun getTopPhrases(): Flow<List<UserStyleEntity>>

    @Query("SELECT * FROM user_style WHERE category = :category ORDER BY frequency DESC LIMIT 20")
    suspend fun getByCategory(category: String): List<UserStyleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(style: UserStyleEntity)

    @Query("UPDATE user_style SET frequency = frequency + 1 WHERE phrase = :phrase")
    suspend fun incrementFrequency(phrase: String)
}
