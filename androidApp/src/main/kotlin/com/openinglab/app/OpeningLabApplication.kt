package com.openinglab.app

import android.app.Application
import com.openinglab.app.content.CourseFeedbackStore
import com.openinglab.app.content.OpeningPresentationCache
import com.openinglab.shared.storage.LearningStore
import com.openinglab.shared.storage.createAndroidLearningStore

class OpeningLabApplication : Application() {
    private val validationPreferences by lazy { getSharedPreferences("bundled-validation", MODE_PRIVATE) }
    val courseValidation by lazy { com.openinglab.app.content.BundledValidationMarkers(validationPreferences,
        com.openinglab.app.content.BundledCourses.TRUSTED_SHA256, BuildConfig.VERSION_CODE,
        com.openinglab.shared.course.DeepCourseValidator.VERSION) }
    val tacticsValidation by lazy { com.openinglab.app.content.BundledValidationMarkers(validationPreferences,
        com.openinglab.app.tactics.BundledTactics.TRUSTED_SHA256, BuildConfig.VERSION_CODE,
        com.openinglab.shared.tactics.TacticsPackValidator.VERSION) }
    val openingPresentationCache = OpeningPresentationCache()
    val learningStore: LearningStore by lazy { createAndroidLearningStore(this) }
    val courseFeedbackStore by lazy { CourseFeedbackStore(java.io.File(filesDir, "course-feedback.json")) }
}
