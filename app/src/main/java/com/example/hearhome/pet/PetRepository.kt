package com.example.hearhome.pet

/**
 * Minimal repository placeholder. Replace with Room DAO later.
 */
class PetRepository {
    private var pet: Pet? = null
    private var plant: Plant? = null

    fun getPet(spaceId: Int): Pet? = pet?.takeIf { it.spaceId == spaceId }
    fun getPlant(spaceId: Int): Plant? = plant?.takeIf { it.spaceId == spaceId }

    fun savePet(p: Pet) { pet = p }
    fun savePlant(pl: Plant) { plant = pl }
}
