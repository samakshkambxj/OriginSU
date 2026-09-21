package com.originsu.manager.domain.model

sealed interface LkmSelection {
    data class LkmUri(val uri: String) : LkmSelection
    data class KmiString(val value: String, val hook: String = "tracepoint") : LkmSelection
    data object KmiNone : LkmSelection
}
