package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserAccountEntity)

    @Query("SELECT COUNT(*) FROM user_accounts")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM user_accounts ORDER BY createdAt DESC")
    suspend fun getAllUsers(): List<UserAccountEntity>

    @Query("UPDATE user_accounts SET name = :newName WHERE LOWER(email) = LOWER(:email)")
    suspend fun updateUserName(email: String, newName: String)

    @Query("UPDATE user_accounts SET role = :newRole WHERE LOWER(email) = LOWER(:email)")
    suspend fun updateUserRole(email: String, newRole: String)

    @Query("UPDATE user_accounts SET isActive = :isActive, isBanned = :isBanned WHERE LOWER(email) = LOWER(:email)")
    suspend fun updateUserStatus(email: String, isActive: Boolean, isBanned: Boolean)

    @Query("UPDATE user_accounts SET apiUsageCount = :usage WHERE LOWER(email) = LOWER(:email)")
    suspend fun updateApiUsage(email: String, usage: Int)

    @Query("DELETE FROM user_accounts WHERE LOWER(email) = LOWER(:email)")
    suspend fun deleteUser(email: String)
}
