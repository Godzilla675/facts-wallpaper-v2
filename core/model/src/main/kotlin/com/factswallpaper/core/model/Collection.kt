package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Collection(
    val id: String,
    val name: String,
    val description: String = "",
    val source: CollectionSource = CollectionSource.USER,
    val accepted: Boolean = true,
    val includeInRotation: Boolean = true,
    val factCount: Int = 0,
    val createdAtEpochMs: Long,
)

@Serializable
data class UserFolder(
    val id: String,
    val name: String,
    val iconName: String = "folder",
    val colorHex: String? = null,
    val includeInRotation: Boolean = true,
    val factCount: Int = 0,
    val createdAtEpochMs: Long,
)

@Serializable
data class FactTag(
    val tag: String,
    val factCount: Int,
)
