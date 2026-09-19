package com.originsu.manager.domain.usecase

import com.originsu.manager.data.AppSettingsRepository

const val SECURE_ROOT_PREF_KEY = "secure_root"

class GetBooleanPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: Boolean = false) =
        repository.getBoolean(key, defaultValue)
}

class SetBooleanPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: Boolean) = repository.putBoolean(key, value)
}

class GetStringPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: String? = null) =
        repository.getString(key, defaultValue)
}

class SetStringPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: String?) = repository.putString(key, value)
}

class GetStringSetPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: Set<String> = emptySet()) =
        repository.getStringSet(key, defaultValue)
}

class SetStringSetPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: Set<String>) = repository.putStringSet(key, value)
}

class RemovePreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String) = repository.remove(key)
}

const val LAST_FLASH_PREF_KEY = "last_flash_time"

class GetLongPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: Long = 0L) =
        repository.getLong(key, defaultValue)
}

class SetLongPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: Long) = repository.putLong(key, value)
}
