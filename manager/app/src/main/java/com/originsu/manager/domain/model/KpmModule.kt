package com.originsu.manager.domain.model

data class KpmModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val args: String = "",
)

data class KpmState(
    val modules: List<KpmModule> = emptyList(),
    val refreshing: Boolean = false,
    val version: String = "",
)
