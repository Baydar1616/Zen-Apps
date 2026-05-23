package com.example.database

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val reminderDao: ReminderDao) {
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()

    suspend fun insert(reminder: Reminder) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun update(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun delete(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun deleteById(id: Int) {
        reminderDao.deleteReminderById(id)
    }
}
