package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val email: String,
    val name: String,
    val passwordHash: String,
    val role: String = "user",
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val apiUsageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
