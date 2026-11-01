package com.pocketcore.assistant.core

import android.content.Context
import android.util.Log

/**
 * Central registry holding all active AssistantModules.
 * Enables zero-touch extensibility: new modules can be plugged in here.
 */
object FeatureRegistry {
    private const val TAG = "FeatureRegistry"

    private val modules = mutableListOf<AssistantModule>()
    private val disabledModuleIds = mutableSetOf<String>()

    /**
     * Registers a module into the assistant ecosystem.
     */
    fun registerModule(module: AssistantModule) {
        if (modules.none { it.id == module.id }) {
            modules.add(module)
            // Sort descending by priority so high-priority specialized handlers check first
            modules.sortByDescending { it.priority }
            Log.d(TAG, "Registered module: ${module.id} (${module.displayName})")
        }
    }

    /**
     * Unregisters a module by its identifier.
     */
    fun unregisterModule(moduleId: String) {
        modules.removeAll { it.id == moduleId }
    }

    /**
     * Returns all registered modules.
     */
    fun getAllModules(): List<AssistantModule> = modules.toList()

    /**
     * Returns all active/enabled modules.
     */
    fun getActiveModules(): List<AssistantModule> {
        return modules.filter { !disabledModuleIds.contains(it.id) }
    }

    /**
     * Toggle a module's enabled status.
     */
    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        if (enabled) {
            disabledModuleIds.remove(moduleId)
        } else {
            disabledModuleIds.add(moduleId)
        }
    }

    fun isModuleEnabled(moduleId: String): Boolean {
        return !disabledModuleIds.contains(moduleId)
    }

    /**
     * Clear all registered modules (used primarily in tests).
     */
    fun clear() {
        modules.clear()
        disabledModuleIds.clear()
    }
}
