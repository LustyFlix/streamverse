package com.lustyflix.streamverse.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lustyflix.streamverse.APIHolder.apis
import com.lustyflix.streamverse.AcraApplication.Companion.getKey
import com.lustyflix.streamverse.AcraApplication.Companion.getKeys
import com.lustyflix.streamverse.AcraApplication.Companion.setKey
import com.lustyflix.streamverse.SearchResponse
import com.lustyflix.streamverse.amap
import com.lustyflix.streamverse.mvvm.Resource
import com.lustyflix.streamverse.mvvm.launchSafe
import com.lustyflix.streamverse.ui.APIRepository
import com.lustyflix.streamverse.utils.Coroutines.ioSafe
import com.lustyflix.streamverse.utils.DataStoreHelper.currentAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

const val SEARCH_HISTORY_KEY = "search_history"

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<List<SearchResponse>>> =
        MutableLiveData()
    val searchResponse: LiveData<Resource<List<SearchResponse>>> get() = _searchResponse

    private val _currentSearch: MutableLiveData<List<OnGoingSearch>> = MutableLiveData()
    val currentSearch: LiveData<List<OnGoingSearch>> get() = _currentSearch

    private val _currentHistory: MutableLiveData<List<SearchHistoryItem>> = MutableLiveData()
    val currentHistory: LiveData<List<SearchHistoryItem>> get() = _currentHistory

    private var repos = synchronized(apis) { apis.map { APIRepository(it) } }

    fun clearSearch() {
        _searchResponse.postValue(Resource.Success(ArrayList()))
        _currentSearch.postValue(emptyList())
    }

    private var currentSearchIndex = 0
    private var onGoingSearch: Job? = null

    fun reloadRepos() {
        repos = synchronized(apis) { apis.map { APIRepository(it) } }
    }

    fun searchAndCancel(
        query: String,
        providersActive: Set<String> = setOf(),
        ignoreSettings: Boolean = false,
        isQuickSearch: Boolean = false,
    ) {
        currentSearchIndex++
        onGoingSearch?.cancel()
        onGoingSearch = search(query, providersActive, ignoreSettings, isQuickSearch)
    }

    fun updateHistory() = viewModelScope.launch {
        ioSafe {
            val items = getKeys("$currentAccount/$SEARCH_HISTORY_KEY")?.mapNotNull {
                getKey<SearchHistoryItem>(it)
            }?.sortedByDescending { it.searchedAt } ?: emptyList()
            _currentHistory.postValue(items)
        }
    }

    private fun search(
        query: String,
        providersActive: Set<String>,
        ignoreSettings: Boolean = false,
        isQuickSearch: Boolean = false,
    ) =
        viewModelScope.launchSafe {
            val currentIndex = currentSearchIndex
            if (query.length <= 1) {
                clearSearch()
                return@launchSafe
            }

            if (!isQuickSearch) {
                val key = query.hashCode().toString()
                setKey(
                    "$currentAccount/$SEARCH_HISTORY_KEY",
                    key,
                    SearchHistoryItem(
                        searchedAt = System.currentTimeMillis(),
                        searchText = query,
                        type = emptyList(), // TODO implement tv type
                        key = key,
                    )
                )
            }

            _searchResponse.postValue(Resource.Loading())


            _currentSearch.postValue(ArrayList())

            withContext(Dispatchers.IO) { // This interrupts UI otherwise
                val currentList = ArrayList<OnGoingSearch>()

                repos.filter { a ->
                    (ignoreSettings || (providersActive.isEmpty() || providersActive.contains(a.name))) && (!isQuickSearch || a.hasQuickSearch)
                }.amap { a -> // Parallel
                    val search = if (isQuickSearch) a.quickSearch(query) else a.search(query)
                    if (currentSearchIndex != currentIndex) return@amap
                    currentList.add(OnGoingSearch(a.name, search))
                    _currentSearch.postValue(currentList)
                }

                if (currentSearchIndex != currentIndex) return@withContext // this should prevent rewrite of existing data bug

                _currentSearch.postValue(currentList)
                val list = ArrayList<SearchResponse>()
                val nestedList =
                    currentList.map { it.data }
                        .filterIsInstance<Resource.Success<List<SearchResponse>>>().map { it.value }

                // I do it this way to move the relevant search results to the top
                var index = 0
                while (true) {
                    var added = 0
                    for (sublist in nestedList) {
                        if (sublist.size > index) {
                            list.add(sublist[index])
                            added++
                        }
                    }
                    if (added == 0) break
                    index++
                }

                _searchResponse.postValue(Resource.Success(list))
            }
        }
}
