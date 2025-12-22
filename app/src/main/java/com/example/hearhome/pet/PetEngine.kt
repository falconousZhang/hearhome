package com.example.hearhome.pet

import kotlin.math.roundToInt

/**
 * Core engine to apply actions and natural decay/growth over time.
 * Intimacy acts as a positive modifier reducing decay and enhancing gains.
 */
class PetEngine {
    /** Apply an action to the attributes and return updated attributes. */
    fun applyAction(attrs: PetAttributes, action: ActionType): PetAttributes {
        val intimacyFactor = 1.0 + (attrs.intimacy / 200.0) // 0.5% per intimacy point
        var mood = attrs.mood
        var health = attrs.health
        var energy = attrs.energy
        var hydration = attrs.hydration
        var intimacy = attrs.intimacy

        when (action) {
            is ActionType.Feed -> {
                energy += (12 * intimacyFactor).roundToInt()
                mood += (6 * intimacyFactor).roundToInt()
            }
            is ActionType.Water -> {
                hydration += (14 * intimacyFactor).roundToInt()
                health += (5 * intimacyFactor).roundToInt()
            }
            is ActionType.Treat -> {
                health += (15 * intimacyFactor).roundToInt()
                mood += (3 * intimacyFactor).roundToInt()
            }
            is ActionType.Play -> {
                mood += (10 * intimacyFactor).roundToInt()
                energy -= 6 // play consumes energy
                intimacy += 5
            }
        }

        return PetAttributes(mood, health, energy, hydration, intimacy).clamp()
    }

    /**
     * Natural decay/growth per tick (e.g., every 30 minutes or 1 hour).
     * Intimacy reduces decay and can slightly boost mood.
     */
    fun tickDecay(attrs: PetAttributes): PetAttributes {
        val decayReducer = 1.0 - (attrs.intimacy / 300.0) // up to ~33% reduction
        var mood = attrs.mood
        var health = attrs.health
        var energy = attrs.energy
        var hydration = attrs.hydration

        // Base decay
        energy -= (4 * decayReducer).roundToInt()
        hydration -= (3 * decayReducer).roundToInt()

        // Health declines if hydration or energy are low
        if (hydration < 30) health -= (4 * decayReducer).roundToInt()
        if (energy < 30) health -= (3 * decayReducer).roundToInt()

        // Slight natural mood gain with high intimacy
        if (attrs.intimacy > 70) mood += 1
        // Mood drops if health is poor
        if (health < 40) mood -= 2

        return PetAttributes(mood, health, energy, hydration, attrs.intimacy).clamp()
    }
}
