package ua.polodarb.flagsChange

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.polodarb.common.Extensions.filterByDisabled
import ua.polodarb.common.Extensions.filterByEnabled
import ua.polodarb.common.Extensions.toSortMap
import ua.polodarb.common.FlagsTypes
import ua.polodarb.domain.override.OverrideFlagsUseCase
import ua.polodarb.domain.override.models.OverriddenFlagsContainer
import ua.polodarb.flagsChange.mappers.mapToExtractBoolData
import ua.polodarb.repository.databases.gms.GmsDBInteractor
import ua.polodarb.repository.databases.gms.GmsDBRepository
import ua.polodarb.repository.databases.local.LocalDBRepository
import ua.polodarb.repository.databases.local.model.SavedFlags
import ua.polodarb.repository.flagsFile.FlagsFromFileRepository
import ua.polodarb.repository.flagsFile.model.LoadedFlagData
import ua.polodarb.repository.flagsFile.model.LoadedFlags
import ua.polodarb.repository.uiStates.UiStates
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

typealias FlagChangeUiStates = UiStates<Map<String, String>>
typealias SavedFlagsFlow = List<SavedFlags>

class FlagChangeScreenViewModel(
    private val pkgName: String,
    private val repository: GmsDBRepository,
    private val roomRepository: LocalDBRepository,
    private val gmsDBInteractor: GmsDBInteractor,
    private val flagsFromFileRepository: FlagsFromFileRepository,
    private val overrideFlagsUseCase: OverrideFlagsUseCase
) : ViewModel() {

    init {
        initUsers()
        getAndroidPackage(pkgName)
    }

    private val _stateBoolean =
        MutableStateFlow<FlagChangeUiStates>(UiStates.Loading())
    val stateBoolean: StateFlow<FlagChangeUiStates> = _stateBoolean.asStateFlow()

    private val _stateInteger =
        MutableStateFlow<FlagChangeUiStates>(UiStates.Loading())
    val stateInteger: StateFlow<FlagChangeUiStates> = _stateInteger.asStateFlow()

    private val _stateFloat =
        MutableStateFlow<FlagChangeUiStates>(UiStates.Loading())
    val stateFloat: StateFlow<FlagChangeUiStates> = _stateFloat.asStateFlow()

    private val _stateString =
        MutableStateFlow<FlagChangeUiStates>(UiStates.Loading())
    val stateString: StateFlow<FlagChangeUiStates> = _stateString.asStateFlow()

    private val _stateSavedFlags =
        MutableStateFlow<SavedFlagsFlow>(emptyList())
    val stateSavedFlags: StateFlow<SavedFlagsFlow> = _stateSavedFlags.asStateFlow()

    val androidPackage: MutableStateFlow<String> = MutableStateFlow(pkgName)


    // Filter
    var filterMethod = mutableStateOf(FilterMethod.ALL)

    // Search
    var searchQuery = mutableStateOf("")

    // Select
    val selectedItems: MutableList<String> =
        Collections.synchronizedList(mutableStateListOf<String>())

    private val changedFilterBoolList = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val changedFilterIntList = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val changedFilterFloatList = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val changedFilterStringList =
        Collections.synchronizedMap(mutableMapOf<String, String>())

    private val listBoolFiltered = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val listIntFiltered = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val listFloatFiltered = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val listStringFiltered = Collections.synchronizedMap(mutableMapOf<String, String>())

    private val usersList = Collections.synchronizedList(mutableListOf<String>())

    // Initialization of flags of all types
    private fun initBoolValues(delay: Boolean = true) {
        collectFlagsFlow(
            loadingState = _stateBoolean,
            errorState = _stateBoolean,
            dataFlow = repository.getBoolFlags(pkgName, delay)
        ) { result ->
            listBoolFiltered.putAll(result.data)
            getBoolFlags()
        }
    }

    suspend fun extractToFile(
        fileName: String,
        packageName: String
    ): Uri {
        return when (val result = stateBoolean.value) {
            is UiStates.Success -> {
                val selectedItemsWithValues = selectedItems.mapToExtractBoolData(result.data)

                flagsFromFileRepository.write(
                    flags = LoadedFlags(
                        packageName = packageName,
                        flags = LoadedFlagData(
                            bool = selectedItemsWithValues
                            // todo: implement other types
                        )
                    ),
                    fileName = fileName
                )
            }

            else -> Uri.EMPTY
        }
    }

    fun initIntValues(delay: Boolean = true) {
        collectFlagsFlow(
            loadingState = _stateInteger,
            errorState = _stateInteger,
            dataFlow = repository.getIntFlags(pkgName, delay)
        ) { result ->
            listIntFiltered.putAll(result.data)
            getIntFlags()
        }
    }

    fun initFloatValues(delay: Boolean = true) {
        collectFlagsFlow(
            loadingState = _stateFloat,
            errorState = _stateFloat,
            dataFlow = repository.getFloatFlags(pkgName, delay)
        ) { result ->
            listFloatFiltered.putAll(result.data)
            getFloatFlags()
        }
    }

    fun initStringValues(delay: Boolean = true) {
        collectFlagsFlow(
            loadingState = _stateString,
            errorState = _stateString,
            dataFlow = repository.getStringFlags(pkgName, delay)
        ) { result ->
            listStringFiltered.putAll(result.data)
            getStringFlags()
        }
    }

    fun initAllFlags() {
        initBoolValues()
        initIntValues()
        initFloatValues()
        initStringValues()
    }


    // Getting initialized flags
    fun getBoolFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            _stateBoolean.value = Dispatchers.Default {
                UiStates.Success(
                    when (filterMethod.value) {
                        FilterMethod.ENABLED -> listBoolFiltered.toMap().filterByEnabled()
                        FilterMethod.DISABLED -> listBoolFiltered.toMap().filterByDisabled()
                        FilterMethod.CHANGED -> changedFilterBoolList
                        else -> listBoolFiltered
                    }.filterBySearchQuery()
                )
            }
        }
    }

    fun getIntFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            _stateInteger.value = Dispatchers.Default {
                UiStates.Success(
                    when (filterMethod.value) {
                        FilterMethod.CHANGED -> changedFilterIntList.filterBySearchQuery()
                        else -> listIntFiltered.filterBySearchQuery()
                    }
                )
            }
        }
    }

    fun getFloatFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            _stateFloat.value = Dispatchers.Default {
                UiStates.Success(
                    when (filterMethod.value) {
                        FilterMethod.CHANGED -> changedFilterFloatList.filterBySearchQuery()
                        else -> listFloatFiltered.filterBySearchQuery()
                    }
                )
            }
        }
    }

    fun getStringFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            _stateString.value = Dispatchers.Default {
                UiStates.Success(
                    when (filterMethod.value) {
                        FilterMethod.CHANGED -> changedFilterStringList.filterBySearchQuery()
                        else -> listStringFiltered.filterBySearchQuery()
                    }
                )
            }
        }
    }

    private fun Map<String, String>.filterBySearchQuery() = filter {
        it.key.contains(searchQuery.value, ignoreCase = true)
    }.toSortMap()

    fun getAllFlags() {
        getBoolFlags()
        getIntFlags()
        getFloatFlags()
        getStringFlags()
    }

    // Updating flags values
    fun updateBoolFlagValues(flagNames: List<String>, newValue: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _stateBoolean.update {
                    val currentState = it
                    if (currentState is UiStates.Success) {
                        val updatedData = currentState.data.toMutableMap()

                        for (flagName in flagNames) {
                            updatedData[flagName] = newValue
                            listBoolFiltered[flagName] = newValue
                        }

                        currentState.copy(data = updatedData.toSortMap())
                    } else {
                        currentState
                    }
                }
            }
        }
    }

    fun updateIntFlagValue(flagName: String, newValue: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _stateInteger.update {
                    val currentState = it
                    if (currentState is UiStates.Success) {
                        val updatedData = currentState.data.toMutableMap()
                        updatedData[flagName] = newValue
                        currentState.copy(data = updatedData.toSortMap())
                    } else {
                        currentState
                    }
                }

                listIntFiltered.replace(flagName, newValue)
            }
        }
    }

    fun updateFloatFlagValue(flagName: String, newValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _stateFloat.value
            if (currentState is UiStates.Success) {
                val updatedData = currentState.data.toMutableMap()
                updatedData[flagName] = newValue
                Dispatchers.Main {
                    _stateFloat.value = currentState.copy(data = updatedData.toSortMap())
                }
                listFloatFiltered.replace(flagName, newValue)
            }
        }
    }

    fun updateStringFlagValue(flagName: String, newValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _stateString.value
            if (currentState is UiStates.Success) {
                val updatedData = currentState.data.toMutableMap()
                updatedData[flagName] = newValue
                _stateString.value = currentState.copy(data = updatedData.toSortMap())
                listStringFiltered.replace(flagName, newValue)
            }
        }
    }

    // Get overridden flags
    fun initOverriddenBoolFlags(pkgName: String) {
        collectFlagsFlow(
            dataFlow = repository.getOverriddenBoolFlagsByPackage(pkgName),
            loadingState = _stateBoolean,
            errorState = _stateBoolean
        ) { result ->
            changedFilterBoolList.clear()
            changedFilterBoolList.putAll(result.data)
            listBoolFiltered.putAll(result.data)
        }
    }

    fun initOverriddenIntFlags(pkgName: String) {
        collectFlagsFlow(
            dataFlow = repository.getOverriddenIntFlagsByPackage(pkgName),
            loadingState = _stateInteger,
            errorState = _stateInteger
        ) { result ->
            changedFilterIntList.clear()
            changedFilterIntList.putAll(result.data)
            listIntFiltered.putAll(result.data)
        }
    }

    fun initOverriddenFloatFlags(pkgName: String) {
        collectFlagsFlow(
            dataFlow = repository.getOverriddenFloatFlagsByPackage(pkgName),
            loadingState = _stateFloat,
            errorState = _stateFloat
        ) { result ->
            changedFilterFloatList.clear()
            changedFilterFloatList.putAll(result.data)
            listFloatFiltered.putAll(result.data)
        }
    }

    fun initOverriddenStringFlags(pkgName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.getOverriddenStringFlagsByPackage(pkgName).collect {
                    when (val data = it) {
                        is UiStates.Success -> {
                            changedFilterStringList.clear()
                            changedFilterStringList.putAll(data.data)
                            listStringFiltered.putAll(data.data)
                        }

                        is UiStates.Loading -> {
                            _stateString.value = UiStates.Loading()
                        }

                        is UiStates.Error -> {
                            _stateString.value = UiStates.Error()
                        }
                    }
                }
            }
        }
    }

    fun initAllOverriddenFlagsByPackage(pkgName: String) {
        initOverriddenBoolFlags(pkgName)
        initOverriddenIntFlags(pkgName)
        initOverriddenFloatFlags(pkgName)
        initOverriddenStringFlags(pkgName)
    }

    // Manually add bool flag
    fun addManuallyBoolFlag(flagName: String, flagValue: String) {
        addManuallyFlag(state = _stateBoolean, flagName = flagName, flagValue = flagValue)
    }

    fun addManuallyIntFlag(flagName: String, flagValue: String) {
        addManuallyFlag(state = _stateInteger, flagName = flagName, flagValue = flagValue)
    }

    fun addManuallyFloatFlag(flagName: String, flagValue: String) {
        addManuallyFlag(state = _stateFloat, flagName = flagName, flagValue = flagValue)
    }

    fun addManuallyStringFlag(flagName: String, flagValue: String) {
        addManuallyFlag(state = _stateString, flagName = flagName, flagValue = flagValue)
    }

    private fun addManuallyFlag(
        state: MutableStateFlow<UiStates<Map<String, String>>>,
        flagName: String,
        flagValue: String
    ) {
        viewModelScope.launch {
            val currentState = state.value
            if (currentState is UiStates.Success) {
                val updatedData = currentState.data.toSortMap()
                updatedData[flagName] = flagValue
                state.value = currentState.copy(data = updatedData)
            }
        }
    }

    // Enable/disable all Bool flags
    private fun turnOnAllBoolFlags() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _stateBoolean.update {
                    val currentState = it
                    if (currentState is UiStates.Success) {
                        val updatedData = currentState.data.mapValues { "1" }.toMutableMap()
                        currentState.copy(data = updatedData.toSortMap())
                    } else {
                        currentState
                    }
                }

                listBoolFiltered.replaceAll { _, _ -> "1" }
            }
        }
    }

    private fun turnOffAllBoolFlags() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _stateBoolean.update {
                    val currentState = it
                    if (currentState is UiStates.Success) {
                        val updatedData = currentState.data.mapValues { "0" }.toMutableMap()
                        currentState.copy(data = updatedData.toSortMap())
                    } else {
                        currentState
                    }
                }

                listBoolFiltered.replaceAll { _, _ -> "0" }
            }
        }
    }


    // Enable/disable selected Bool flags
    fun enableSelectedFlag() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                overrideFlagsUseCase.invoke(
                    packageName = pkgName,
                    OverriddenFlagsContainer(
                        boolValues = listBoolFiltered.filter { selectedItems.contains(it.key) && it.value == "0" }.map { (key, _) -> key to "1" }.toMap(),
                        intValues = listIntFiltered.filter { selectedItems.contains(it.key) },
                        floatValues = listFloatFiltered.filter { selectedItems.contains(it.key) },
                        stringValues = listStringFiltered.filter { selectedItems.contains(it.key) }
                    )
                )
                if ((stateBoolean.value as UiStates.Success<Map<String, String>>).data.keys.size == selectedItems.size) {
                    turnOnAllBoolFlags()
                } else {
                    updateBoolFlagValues(selectedItems, "1")
                }
            }
        }
    }

    fun disableSelectedFlag() {
        viewModelScope.launch(Dispatchers.IO) {
            overrideFlagsUseCase.invoke(
                packageName = pkgName,
                OverriddenFlagsContainer(
                    boolValues = listBoolFiltered.filter { selectedItems.contains(it.key) && it.value == "1" }.map { (key, _) -> key to "0" }.toMap(),
                    intValues = listIntFiltered.filter { selectedItems.contains(it.key) },
                    floatValues = listFloatFiltered.filter { selectedItems.contains(it.key) },
                    stringValues = listStringFiltered.filter { selectedItems.contains(it.key) }
                )
            )
            if ((stateBoolean.value as UiStates.Success<Map<String, String>>).data.keys.size == selectedItems.size) {
                turnOffAllBoolFlags()
            } else {
                updateBoolFlagValues(selectedItems, "0")
            }
        }
    }

    // Init users
    private fun initUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getUsers().collect {
                usersList.addAll(it)
            }
        }
    }


    // Get original Android package
    private fun getAndroidPackage(pkgName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.getAndroidPackage(pkgName).collect {
                    androidPackage.value = it
                }
            }
        }
    }

    suspend fun overrideFlag(
        packageName: String,
        flags: OverriddenFlagsContainer
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            overrideFlagsUseCase(
                packageName = packageName,
                flags = flags
            )
        }
    }

    // Override Flag
    fun overrideFlag(
        packageName: String,
        name: String,
        flagType: Int = 0,
        intVal: String? = null,
        boolVal: String? = null,
        floatVal: String? = null,
        stringVal: String? = null,
        extensionVal: String? = null,
        committed: Int = 0,
        clearData: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            gmsDBInteractor.overrideFlag(
                packageName = packageName,
                name = name,
                flagType = flagType,
                intVal = intVal,
                boolVal = boolVal,
                floatVal = floatVal,
                stringVal = stringVal,
                extensionVal = extensionVal,
                committed = committed,
                clearData = clearData,
                usersList = usersList
            )
        }
        changedFilterBoolList[name] = boolVal
        changedFilterIntList[name] = intVal
        changedFilterFloatList[name] = floatVal
        changedFilterStringList[name] = stringVal
    }

    fun clearPhenotypeCache(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gmsDBInteractor.clearPhenotypeCache(pkgName)
        }
    }

    // Delete overridden flags
    fun deleteOverriddenFlagByPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteOverriddenFlagByPackage(packageName)
        }
    }

    // Reset int/float/string flags to default value
    fun resetOtherTypesFlagsToDefault(flag: String) {
        viewModelScope.launch(Dispatchers.IO){
            repository.deleteRowByFlagName(
                packageName = pkgName,
                name = flag
            )
        }
    }

    // Saved flags in local DB
    fun getAllSavedFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            roomRepository.getSavedFlags().collect {
                _stateSavedFlags.value = it
            }
        }
    }

    fun saveFlag(flagName: String, pkgName: String, flagType: FlagsTypes) {
        viewModelScope.launch(Dispatchers.IO) {
            roomRepository.saveFlag(flagName, pkgName, flagType)
        }
    }

    fun deleteSavedFlag(flagName: String, pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            roomRepository.deleteSavedFlag(flagName, pkgName)
        }
    }

    fun saveSelectedFlags() {
        viewModelScope.launch(Dispatchers.IO) {
            selectedItems.forEach {
                saveFlag(
                    it,
                    pkgName,
                    FlagsTypes.BOOLEAN
                )
            }
        }
    }

    // Select all items by one click
    fun selectAllItems() {
        viewModelScope.launch(Dispatchers.IO) {
            handleUiStates(
                stateBoolean.value,
                loadingState = _stateBoolean,
                errorState = _stateBoolean
            ) { result ->
                selectedItems.clear()
                selectedItems.addAll(result.data.keys)
            }
        }
    }

    // ProgressDialog
    val showProgressDialog = mutableStateOf(false)
    fun showFalseProgressDialog(customCount: Int? = null) {
        viewModelScope.launch {
            handleUiStates(
                stateBoolean.value,
                loadingState = _stateBoolean,
                errorState = _stateBoolean
            ) { result ->
                showProgressDialog.value = true
                val flagsCount = customCount ?: result.data.keys.size
                delay(
                    when {
                        flagsCount <= 50 -> 0
                        flagsCount in 51..150 -> 3000
                        flagsCount in 151..500 -> 5000
                        flagsCount in 501..1000 -> 7000
                        flagsCount in 1001..1500 -> 9000
                        flagsCount > 1501 -> 10000
                        else -> 0
                    }
                ) // todo: refactor!!
                showProgressDialog.value = false
            }
        }
    }

    private fun <T> collectFlagsFlow(
        dataFlow: Flow<UiStates<T>>,
        loadingState: MutableStateFlow<FlagChangeUiStates>,
        errorState: MutableStateFlow<FlagChangeUiStates>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        onSuccess: suspend (UiStates.Success<T>) -> Unit
    ) {
        viewModelScope.launch(coroutineContext) {
            dataFlow.collect { uiStates ->
                handleUiStates(
                    uiStates = uiStates,
                    loadingState = loadingState,
                    errorState = errorState,
                    onSuccess = onSuccess
                )
            }
        }
    }

    private suspend fun <T> handleUiStates(
        uiStates: UiStates<T>,
        loadingState: MutableStateFlow<FlagChangeUiStates>,
        errorState: MutableStateFlow<FlagChangeUiStates> = _stateInteger,
        onSuccess: suspend (UiStates.Success<T>) -> Unit
    ) {
        when (uiStates) {
            is UiStates.Success -> onSuccess(uiStates)

            is UiStates.Loading -> loadingState.value = UiStates.Loading()

            is UiStates.Error -> errorState.value = UiStates.Error()
        }
    }

    fun resetFilterLists() {
        viewModelScope.launch(Dispatchers.Default) {
            listBoolFiltered.clear()
            listIntFiltered.clear()
            listFloatFiltered.clear()
            listStringFiltered.clear()
            changedFilterBoolList.clear()
            changedFilterIntList.clear()
            changedFilterFloatList.clear()
            changedFilterStringList.clear()
        }
    }
}

enum class FilterMethod : MutableState<FilterMethod> {
    ALL, ENABLED, DISABLED, CHANGED;

    override var value: FilterMethod
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun component1(): FilterMethod {
        TODO("Not yet implemented")
    }

    override fun component2(): (FilterMethod) -> Unit {
        TODO("Not yet implemented")
    }
}
