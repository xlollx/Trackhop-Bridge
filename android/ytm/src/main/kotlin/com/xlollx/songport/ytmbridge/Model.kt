package com.xlollx.songport.ytmbridge

import kotlinx.serialization.Serializable

/** What crosses the process boundary to Trackhop. Field names are the contract: do not rename. */
@Serializable
data class PlaylistDto(
    val id: String, val name: String, val count: Int = -1,
    /** False for a playlist followed from someone else: readable, not writable. */
    val owned: Boolean = true,
    val description: String = "",
)

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String = "",
    val durationMs: Long = 0,
    /** Id of the entry inside the playlist: needed to remove it. */
    val setVideoId: String? = null,
    /** Native uri (Spotify), when the id alone is not what the service wants back. */
    val uri: String? = null,
    val explicit: Boolean? = null,
    val addedAt: Long? = null,
    /** "album" or "artist" for library objects; null for a track. */
    val kind: String? = null,
    val year: Int? = null,
    /** False when the service lists the entry greyed out (it no longer plays); null when it did not say. */
    val available: Boolean? = null,
)

@Serializable
data class RemoveItem(val videoId: String, val setVideoId: String? = null)
