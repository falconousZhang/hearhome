package com.example.hearhome.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.remote.ApiPetAttributes
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.ApiSpacePet
import com.example.hearhome.data.remote.ApiSpacePetRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PetViewModel(
    private val repo: PetRepository = PetRepository(),
    private val engine: PetEngine = PetEngine()
) : ViewModel() {
    private val _petState = MutableStateFlow<Pet?>(null)
    val petState: StateFlow<Pet?> = _petState

    fun initPet(spaceId: Int, name: String = "萌宠") {
        val existing = repo.getPet(spaceId)
        val pet = existing ?: Pet(id = 1, spaceId = spaceId, name = name)
        repo.savePet(pet)
        _petState.value = pet
    }

    fun applyAction(action: ActionType) {
        val current = _petState.value ?: return
        val updatedAttrs = engine.applyAction(current.attributes, action)
        val updated = current.copy(attributes = updatedAttrs)
        repo.savePet(updated)
        _petState.value = updated
        syncPetAsync(updated)
    }

    /** Trigger periodic decay from a scheduler (WorkManager/Timer). */
    fun tick() {
        val current = _petState.value ?: return
        val updatedAttrs = engine.tickDecay(current.attributes)
        val updated = current.copy(attributes = updatedAttrs)
        repo.savePet(updated)
        _petState.value = updated
        syncPetAsync(updated)
    }

    /** Update intimacy from space relationship changes. */
    fun updateIntimacy(value: Int) {
        val current = _petState.value ?: return
        val updated = current.copy(attributes = current.attributes.copy(intimacy = value).clamp())
        repo.savePet(updated)
        _petState.value = updated
        syncPetAsync(updated)
    }

    fun refreshFromCloud(spaceId: Int) {
        viewModelScope.launch {
            runCatching { ApiService.getSpacePet(spaceId) }
                .onSuccess { remote ->
                    val pet = remote.toDomain()
                    repo.savePet(pet)
                    _petState.value = pet
                }
        }
    }

    fun syncCurrentPet() {
        _petState.value?.let { syncPetAsync(it) }
    }

    private fun syncPetAsync(pet: Pet) {
        viewModelScope.launch {
            runCatching { ApiService.saveSpacePet(pet.spaceId, pet.toApiRequest()) }
                .onSuccess { remote ->
                    val synced = remote.toDomain()
                    repo.savePet(synced)
                    _petState.value = synced
                }
        }
    }
}

private fun ApiSpacePet.toDomain(): Pet = Pet(
    id = id,
    spaceId = spaceId,
    name = name,
    attributes = attributes.toDomain()
)

private fun Pet.toApiRequest(): ApiSpacePetRequest = ApiSpacePetRequest(
    name = name,
    type = "pet",
    attributes = attributes.toApi()
)

private fun PetAttributes.toApi(): ApiPetAttributes = ApiPetAttributes(
    mood = mood,
    health = health,
    energy = energy,
    hydration = hydration,
    intimacy = intimacy
)

private fun ApiPetAttributes.toDomain(): PetAttributes = PetAttributes(
    mood = mood,
    health = health,
    energy = energy,
    hydration = hydration,
    intimacy = intimacy
)
