package com.example.hearhome.pet

// Basic attribute container for pets/plants
data class PetAttributes(
    val mood: Int = 50,        // 0..100
    val health: Int = 80,      // 0..100
    val energy: Int = 60,      // 0..100
    val hydration: Int = 60,   // 0..100 (plants use more)
    val intimacy: Int = 50     // 0..100 relationship intimacy in the space
) {
    fun clamp() = copy(
        mood = mood.coerceIn(0, 100),
        health = health.coerceIn(0, 100),
        energy = energy.coerceIn(0, 100),
        hydration = hydration.coerceIn(0, 100),
        intimacy = intimacy.coerceIn(0, 100)
    )
}

// Pet entity
data class Pet(
    val id: Int,
    val spaceId: Int,
    val name: String,
    val attributes: PetAttributes = PetAttributes()
)

// Plant entity (shares attributes, emphasis on hydration/health)
data class Plant(
    val id: Int,
    val spaceId: Int,
    val name: String,
    val attributes: PetAttributes = PetAttributes()
)

// Available actions for interaction
sealed class ActionType {
    data object Feed : ActionType()            // pet: energy+, mood+
    data object Water : ActionType()           // plant: hydration+, health+
    data object Treat : ActionType()           // pet/plant: health+
    data object Play : ActionType()            // pet: mood++, energy-
}
