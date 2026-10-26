package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 7. Collections (user decks and AI-suggested decks). */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val source: String = "USER",
    val accepted: Boolean = true,
    val includeInRotation: Boolean = true,
    val createdAtEpochMs: Long,
)

/** 8. Collection <-> fact many-to-many join table. */
@Entity(
    tableName = "collection_fact_cross_ref",
    primaryKeys = ["collectionId", "factId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FactItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["factId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId"), Index("factId")]
)
data class CollectionFactCrossRef(
    val collectionId: String,
    val factId: String,
    val sortOrder: Int = 0,
)

/** 9. User folders for organization. */
@Entity(tableName = "user_folders")
data class UserFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String = "folder",
    val colorHex: String? = null,
    val includeInRotation: Boolean = true,
    val createdAtEpochMs: Long,
)

/** 10. Folder <-> fact many-to-many join table. */
@Entity(
    tableName = "folder_fact_cross_ref",
    primaryKeys = ["folderId", "factId"],
    foreignKeys = [
        ForeignKey(
            entity = UserFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FactItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["factId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("folderId"), Index("factId")]
)
data class FolderFactCrossRef(
    val folderId: String,
    val factId: String,
)
