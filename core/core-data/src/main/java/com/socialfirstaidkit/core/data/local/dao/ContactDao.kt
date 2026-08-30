package com.socialfirstaidkit.core.data.local.dao

import androidx.room.*
import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastInteraction DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeContactById(id: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE displayName LIKE :name LIMIT 1")
    suspend fun findByName(name: String): ContactEntity?

    @Transaction
    suspend fun upsert(contact: ContactEntity) {
        if (insert(contact) == -1L) {
            update(contact)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)
}
