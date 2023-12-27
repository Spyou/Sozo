package com.animestudios.animeapp.anilist.response

import com.animestudios.animeapp.tools.FileUrl
import com.animestudios.animeapp.parsers.VideoExtractor
import java.io.Serializable
data class Episode(
    val number: String,
    var link: String? = null,
    var title: String? = null,
    var desc: String? = null,
    var thumb: FileUrl? = null,
    var filler: Boolean = false,
    var selectedExtractor: String? = null,
    var selectedVideo: Int = 0,
    var selectedSubtitle: Int? = -1,
    var extractors: MutableList<VideoExtractor>?=null,
    @Transient var extractorCallback: ((VideoExtractor) -> Unit)?=null,
    var allStreams: Boolean = false,
    var watched: Long? = null,
    var maxLength: Long? = null,
    val extra: Map<String,String>?=null,

    ) : Serializable

