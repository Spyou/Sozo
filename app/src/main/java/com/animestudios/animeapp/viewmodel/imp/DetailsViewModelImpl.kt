package com.animestudios.animeapp.viewmodel.imp

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animestudios.animeapp.anilist.repo.imp.AniListRepositoryImp
import com.animestudios.animeapp.anilist.response.Episode
import com.animestudios.animeapp.jikan.Jikan
import com.animestudios.animeapp.kitsu.Kitsu
import com.animestudios.animeapp.media.Media
import com.animestudios.animeapp.media.Selected
import com.animestudios.animeapp.parsers.ShowResponse
import com.animestudios.animeapp.parsers.VideoExtractor
import com.animestudios.animeapp.readData
import com.animestudios.animeapp.saveData
import com.animestudios.animeapp.sourcers.WatchSources
import com.animestudios.animeapp.tools.tryWithSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DetailsViewModelImpl : ViewModel() {
    private val repositoryImp: AniListRepositoryImp = AniListRepositoryImp()
    val scrolledToTop = MutableLiveData(true)

    fun saveSelected(id: Int, data: Selected, activity: Activity? = null) {
        saveData("$id-select", data, activity)
    }

    fun loadSelected(media: Media): Selected {
        return readData<Selected>("${media.id}-select") ?: Selected().let {
            it.source = if (media.isAdult) 0 else when (media.anime != null) {
                true -> readData("settings_def_anime_source") ?: 0
                else -> readData("settings_def_manga_source") ?: 0
            }
            it.preferDub = readData("settings_prefer_dub") ?: false
            saveSelected(media.id, it)
            it
        }
    }

    var continueMedia: Boolean? = null
    private var loading = false

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m: Media) {
        if (!loading) {
            loading = true
            repositoryImp.getFullDataById(m).onEach {
                media.postValue(it)
            }.launchIn(viewModelScope)
        }
        loading = false
    }

    fun setMedia(m: Media) {
        media.postValue(m)
    }

    val responses = MutableLiveData<List<ShowResponse>?>(null)


    //Anime
    private val animeEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getKitsuEpisodes(): LiveData<Map<String, Episode>> = animeEpisodes
    suspend fun loadKitsuEpisodes(s: Media) {
        tryWithSuspend {
            if (animeEpisodes.value == null) animeEpisodes.postValue(Kitsu.getKitsuEpisodesDetails(s))
        }
    }

    private val fillerEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getFillerEpisodes(): LiveData<Map<String, Episode>> = fillerEpisodes
    suspend fun loadFillerEpisodes(s: Media) {
        tryWithSuspend {
            if (fillerEpisodes.value == null) fillerEpisodes.postValue(
                Jikan.getEpisodes(
                    s.idMAL ?: return@tryWithSuspend
                )
            )
        }
    }

    var watchSources: WatchSources? = null

    private val episodes = MutableLiveData<MutableMap<Int, MutableMap<String, Episode>>>(null)
    private val epsLoaded = mutableMapOf<Int, MutableMap<String, Episode>>()
    fun getEpisodes(): LiveData<MutableMap<Int, MutableMap<String, Episode>>> = episodes
    suspend fun loadEpisodes(media: Media, i: Int) {
        if (!epsLoaded.containsKey(i)) {
            epsLoaded[i] = watchSources?.loadEpisodesFromMedia(i, media) ?: return
        }
        episodes.postValue(epsLoaded)
    }

    suspend fun forceLoadEpisode(media: Media, i: Int) {
        epsLoaded[i] = watchSources?.loadEpisodesFromMedia(i, media) ?: return
        episodes.postValue(epsLoaded)
    }

    suspend fun overrideEpisodes(i: Int, source: ShowResponse, id: Int) {
        watchSources?.saveResponse(i, id, source)
        epsLoaded[i] = watchSources?.loadEpisodes(i, source.link, source.extra) ?: return
        episodes.postValue(epsLoaded)
    }

    private var episode = MutableLiveData<Episode?>(null)
    fun getEpisode(): LiveData<Episode?> = episode

    suspend fun loadEpisodeVideos(ep: Episode, i: Int, post: Boolean = true) {
        val link = ep.link ?: return
        if (!ep.allStreams || ep.extractors.isNullOrEmpty()) {
            val list = mutableListOf<VideoExtractor>()
            ep.extractors = list
            watchSources?.get(i)?.apply {
                if (!post && !allowsPreloading) return@apply
                loadByVideoServers(link, ep.extra) {
                    if (it.videos.isNotEmpty()) {
                        list.add(it)
                        ep.extractorCallback?.invoke(it)
                    }
                }
                ep.extractorCallback = null
                if (list.isNotEmpty())
                    ep.allStreams = true
            }
        }


        if (post) {
            episode.postValue(ep)
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
        }
    }


}