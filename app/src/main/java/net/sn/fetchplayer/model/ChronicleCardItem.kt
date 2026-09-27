package net.sn.fetchplayer.model

import java.io.Serializable

data class ChronicleCardItem(
    val id: Int,
    val chapter: String,
    val image: String,
    val audio: String,
    val text: String
) : Serializable
