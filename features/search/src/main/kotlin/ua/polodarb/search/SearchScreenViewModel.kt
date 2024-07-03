package ua.polodarb.search

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.polodarb.domain.override.OverrideFlagsUseCase
import ua.polodarb.domain.override.models.OverriddenFlagsContainer
import ua.polodarb.repository.appsList.AppInfo
import ua.polodarb.repository.appsList.AppsListRepository
import ua.polodarb.repository.databases.gms.GmsDBRepository
import ua.polodarb.repository.databases.local.LocalDBRepository
import ua.polodarb.repository.suggestedFlags.SuggestedFlagsRepository
import ua.polodarb.repository.suggestedFlags.models.FlagDetails
import ua.polodarb.repository.uiStates.UiStates
import ua.polodarb.search.constants.SortingTypeConstants.APP_NAME
import ua.polodarb.search.constants.SortingTypeConstants.APP_NAME_REVERSED
import ua.polodarb.search.constants.SortingTypeConstants.LAST_UPDATE
import ua.polodarb.search.constants.SortingTypeConstants.PACKAGE_NAME
import java.util.Collections

typealias AppInfoList = UiStates<PersistentList<AppInfo>>
typealias AppDialogList = UiStates<PersistentList<String>>
typealias PackagesScreenUiStates = UiStates<Map<String, String>>
typealias AllFlagsScreenUiStates = UiStates<List<FlagDetails>>

class SearchScreenViewModel(
    private val repository: AppsListRepository,
    private val gmsRepository: GmsDBRepository,
    private val roomRepository: LocalDBRepository,
    private val mergedFlags: SuggestedFlagsRepository,
    private val overrideFlagsUseCase: OverrideFlagsUseCase
) : ViewModel() {

    // Apps List
    private val _appsListUiState =
        MutableStateFlow<AppInfoList>(UiStates.Loading())
    val appsListUiState: StateFlow<AppInfoList> = _appsListUiState.asStateFlow()

    private val _dialogDataState =
        MutableStateFlow<AppDialogList>(UiStates.Loading())
    val dialogDataState: StateFlow<AppDialogList> = _dialogDataState.asStateFlow()

    private val _dialogPackage = MutableStateFlow("")
    val dialogPackage: StateFlow<String> = _dialogPackage.asStateFlow()


    // Packages List
    private val _packagesListUiState = MutableStateFlow<PackagesScreenUiStates>(UiStates.Loading())
    val packagesListUiState: StateFlow<PackagesScreenUiStates> = _packagesListUiState.asStateFlow()

    private val _stateSavedPackages =
        MutableStateFlow<List<String>>(emptyList())
    val stateSavedPackages: StateFlow<List<String>> = _stateSavedPackages.asStateFlow()

    // Search and filter
    var appsSearchQuery = mutableStateOf("")
    private val appsListFiltered: MutableList<AppInfo> = mutableListOf()

    var packagesSearchQuery = mutableStateOf("")
    private val packagesListFiltered: MutableMap<String, String> = mutableMapOf()

    private val usersList = Collections.synchronizedList(mutableListOf<String>())

    fun clearSearchQuery() {
        appsSearchQuery.value = ""
        packagesSearchQuery.value = ""
    }

    // Sorting
    val sortType = mapOf(
        APP_NAME to "By app name",
        APP_NAME_REVERSED to "By app name (Reversed)",
        LAST_UPDATE to "By last update",
        PACKAGE_NAME to "By package name"
    )

    var setSortType = mutableStateOf(sortType[APP_NAME])

    init {
        initUsers()
        initGms()
    }

    fun initGms() {
        appsListFiltered.clear()
        initAllInstalledApps()
        initGmsPackagesList()
        getAllSavedPackages()
//        initAllFlags()
    }

    private fun initUsers() {
        viewModelScope.launch {
            gmsRepository.getUsers().collect {
                usersList.addAll(it)
            }
        }
    }

    fun setPackageToDialog(pkgName: String) {
        _dialogPackage.value = pkgName
    }

    fun setEmptyList() {
        _dialogDataState.value = UiStates.Success(persistentListOf())
    }

    /**
     * **AppsListScreen** - get list of packages in app
     */
    fun getListByPackages(pkgName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.getListByPackages(pkgName).collect { uiStates ->
                    when (uiStates) {
                        is UiStates.Success -> {
                            _dialogDataState.value =
                                UiStates.Success(uiStates.data.toPersistentList())
                        }

                        is UiStates.Loading -> {
                            _dialogDataState.value = UiStates.Loading()
                        }

                        is UiStates.Error -> {
                            _dialogDataState.value = UiStates.Error()
                        }
                    }
                }
            }
        }
    }

    /**
     * **AppsListScreen** - init list of all installed apps
     */
    private fun initAllInstalledApps() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.getAllInstalledApps().collectLatest { uiStates ->
                    when (uiStates) {
                        is UiStates.Success -> {
                            appsListFiltered.addAll(uiStates.data)
                            getAllInstalledApps()
                        }

                        is UiStates.Loading -> {
                            _appsListUiState.value = UiStates.Loading()
                        }

                        is UiStates.Error -> {
                            _appsListUiState.value = UiStates.Error()
                        }
                    }
                }
            }
        }
    }

    /**
     * **AppsListScreen** - get list of all installed apps
     */
    fun getAllInstalledApps() {
        if (appsListFiltered.isNotEmpty()) {
            _appsListUiState.value = UiStates.Success(
                appsListFiltered.filter {
                    it.appName.contains(appsSearchQuery.value, ignoreCase = true)
                }.let { filteredList ->
                    when (setSortType.value) {
                        sortType[PACKAGE_NAME] -> filteredList.sortedBy { it.applicationInfo.packageName }
                        sortType[APP_NAME], sortType[APP_NAME_REVERSED] -> {
                            if (setSortType.value == sortType[APP_NAME_REVERSED]) {
                                filteredList.sortedByDescending { it.appName }
                            } else {
                                filteredList.sortedBy { it.appName }
                            }
                        }
                        sortType[LAST_UPDATE] -> {
                            filteredList.sortedByDescending {
                                if (it.packageInfo != null) {
                                    it.packageInfo?.lastUpdateTime
                                } else {
                                    0L
                                }
                            }
                        }
                        else -> filteredList
                    }
                }.toPersistentList()
            )
        }
    }



    private fun initGmsPackagesList() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                gmsRepository.getGmsPackages().collect { uiState ->
                    when (uiState) {
                        is UiStates.Success -> {
                            packagesListFiltered.putAll(uiState.data)
                            getGmsPackagesList()
                        }

                        is UiStates.Loading -> {
                            _packagesListUiState.value = UiStates.Loading()
                        }

                        is UiStates.Error -> {
                            _packagesListUiState.value = UiStates.Error()
                        }
                    }
                }
            }
        }
    }

    fun getGmsPackagesList() {
        if (packagesListFiltered.isNotEmpty()) {
            _packagesListUiState.value = UiStates.Success(
                packagesListFiltered.filter {
                    it.key.contains(packagesSearchQuery.value, ignoreCase = true)
                }.toSortedMap()
            )
        }
    }

    private fun getAllSavedPackages() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                roomRepository.getSavedPackages().collect {
                    _stateSavedPackages.value = it
                }
            }
        }
    }

    fun savePackage(pkgName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                roomRepository.savePackage(pkgName)
            }
        }
    }

    fun deleteSavedPackage(pkgName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                roomRepository.deleteSavedPackage(pkgName)
            }
        }
    }

    fun addPackageToDB(
        packageName: String
    ) {
        viewModelScope.launch {
            overrideFlagsUseCase.invoke(
                packageName = packageName,
                flags = OverriddenFlagsContainer()
            )
        }
    }
}
