package com.openinglab.app

import android.app.Application
import com.openinglab.shared.storage.LearningStore
import com.openinglab.shared.storage.createAndroidLearningStore

class OpeningLabApplication : Application() {
    val learningStore: LearningStore by lazy { createAndroidLearningStore(this) }
}
