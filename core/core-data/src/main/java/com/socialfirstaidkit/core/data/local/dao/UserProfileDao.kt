package com.socialfirstaidkit.core.data.local.dao

import androidx.room.*
import com.socialfirstaidkit.core.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET nickname = :nickname, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateNickname(nickname: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET avatarUri = :uri, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateAvatar(uri: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET gender = :gender, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateGender(gender: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET age = :age, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateAge(age: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET comicAvatarUrl = :url, comicAvatarGeneratedAt = :generatedAt, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateComicAvatar(url: String?, generatedAt: Long? = System.currentTimeMillis(), timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profile WHERE id = 1")
    suspend fun delete()
}
