package com.openinglab.shared.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createJvmLearningDatabase(path: String): LearningDatabase = Room.databaseBuilder<LearningDatabase>(name = path)
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(LearningDatabase.MIGRATION_1_2).build()
