package com.openinglab.shared.data

import com.openinglab.shared.model.Opening

interface OpeningRepository {
    fun getOpenings(): List<Opening>
    fun getOpening(id: String): Opening
    fun searchOpenings(query: String): List<Opening>
}

class OfflineFirstOpeningRepository : OpeningRepository {
    override fun getOpenings(): List<Opening> = OpeningCatalog.openings
    override fun getOpening(id: String): Opening = OpeningCatalog.byId(id)
    override fun searchOpenings(query: String): List<Opening> = OpeningCatalog.search(query)
}

