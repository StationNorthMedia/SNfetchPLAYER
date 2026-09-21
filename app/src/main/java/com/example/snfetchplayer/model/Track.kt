package com.example.snfetchplayer.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val youtubeId: String? = null,
    val resolvedStreamUrl: String? = null,
    val isStationContent: Boolean = false,
    val isLocalVideo: Boolean = false,
    val localResourceUri: String? = null
)
