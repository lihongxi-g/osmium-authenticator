package com.safekey.authenticator.model

/** A local category tag. It never leaves the device except inside encrypted backups. */
data class Tag(
    val id: String,
    val name: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long
)
