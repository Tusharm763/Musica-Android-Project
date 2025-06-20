package com.musicfirebase.app.model

import java.io.Serializable

data class Song(
    val id: String = "",
    val name: String = "",
    val url: String = ""
)  : Serializable {
    constructor() : this("", "", "")
}