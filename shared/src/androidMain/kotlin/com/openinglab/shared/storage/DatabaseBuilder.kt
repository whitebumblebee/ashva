package com.openinglab.shared.storage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createAndroidLearningDatabase(context: Context, databaseName: String = "opening-lab.db"): LearningDatabase = Room.databaseBuilder<LearningDatabase>(
    context.applicationContext, context.getDatabasePath(databaseName).absolutePath)
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(LearningDatabase.MIGRATION_1_2).build()

fun createAndroidLearningStore(context: Context): LearningStore = RoomLearningStore(createAndroidLearningDatabase(context))
