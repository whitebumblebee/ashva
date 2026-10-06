// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import kotlinx.coroutines.Dispatchers

/** One shared CPU budget for course/tactics/catalog preparation, including background validation. */
object StartupDispatchers {
    val worker = Dispatchers.Default.limitedParallelism(2)
    val background = worker.limitedParallelism(1)
}
