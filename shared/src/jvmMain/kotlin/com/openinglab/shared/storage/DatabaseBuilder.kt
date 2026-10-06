package com.openinglab.shared.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createJvmLearningDatabase(path: String): LearningDatabase = Room.databaseBuilder<LearningDatabase>(name = path)
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4, LearningDatabase.MIGRATION_4_5, LearningDatabase.MIGRATION_5_6, LearningDatabase.MIGRATION_6_7, LearningDatabase.MIGRATION_7_8).build()
