package com.openinglab.shared.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Caller supplies an application-support path; no iOS UI/application is created here. */
fun createIosLearningDatabase(path: String): LearningDatabase = Room.databaseBuilder<LearningDatabase>(name = path)
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default)
    .addMigrations(LearningDatabase.MIGRATION_1_2).build()
