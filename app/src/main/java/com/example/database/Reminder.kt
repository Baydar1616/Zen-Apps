package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val progress: Int = 0, // Progress (0 to 100%)
    val dueDate: Long,
    val priority: String, // "LOW", "MEDIUM", "HIGH"
    val fishType: Int = 0, // Index: 0 = Goldfish, 1 = Betta, 2 = Neon Tetra, 3 = Coral Tang, 4 = Purple Damsel
    val isCompleted: Boolean = false,
    val createdDate: Long = System.currentTimeMillis()
)
