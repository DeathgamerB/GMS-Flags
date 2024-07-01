package ua.polodarb.flagsChange

import android.content.Intent
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.createChooser
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ua.polodarb.common.Extensions.toSortMap
import ua.polodarb.common.FlagsTypes
import ua.polodarb.domain.override.models.OverriddenFlagsContainer
import ua.polodarb.flagsChange.dialogs.AddFlagDialog
import ua.polodarb.flagsChange.dialogs.ProgressDialog
import ua.polodarb.flagsChange.dialogs.ShareFlagsDialog
import ua.polodarb.flagsChange.dialogs.SuggestFlagsDialog
import ua.polodarb.flagsChange.flagsType.BooleanFlagsScreen
import ua.polodarb.flagsChange.flagsType.OtherTypesFlagsScreen
import ua.polodarb.flagschange.R
import ua.polodarb.repository.uiStates.UiStates
import ua.polodarb.ui.components.chips.filter.GFlagFilterChipRow
import ua.polodarb.ui.components.dialogs.ReportFlagsDialog
import ua.polodarb.ui.components.dropDown.FlagChangeDropDown
import ua.polodarb.ui.components.dropDown.FlagSelectDropDown
import ua.polodarb.ui.components.searchBar.GFlagsSearchBar
import ua.polodarb.ui.components.tabs.GFlagsTabRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FlagChangeScreen(
    onBackPressed: () -> Unit,
    packageName: String?,
    onAddMultipleFlags: (packageName: String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val viewModel =
        koinViewModel<FlagChangeScreenViewModel>(parameters = { parametersOf(packageName) })

    val uiStateBoolean = viewModel.stateBoolean.collectAsState()
    val uiStateInteger = viewModel.stateInteger.collectAsState()
    val uiStateFloat = viewModel.stateFloat.collectAsState()
    val uiStateString = viewModel.stateString.collectAsState()

    val savedFlags = viewModel.stateSavedFlags.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initAllFlags()
        viewModel.getAllSavedFlags()
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()


    // Tab bar
    var tabState by remember { mutableIntStateOf(0) }
    val titles = persistentListOf("Bool", "Int", "Float", "String")

    val pagerState = rememberPagerState(pageCount = {
        4 // 5 with extVal
    })


    // Select states
    var isInSelectionMode by rememberSaveable {
        mutableStateOf(false)
    }

    val resetSelectionMode = {
        isInSelectionMode = false
        viewModel.selectedItems.clear()
    }

    BackHandler(
        enabled = isInSelectionMode,
    ) {
        resetSelectionMode()
    }

    LaunchedEffect(
        key1 = isInSelectionMode,
        key2 = viewModel.selectedItems.size,
    ) {
        if (isInSelectionMode && viewModel.selectedItems.isEmpty()) {
            isInSelectionMode = false
        }
    }

    // Filter
    var selectedChips by remember { mutableIntStateOf(0) }
    val chipsList = persistentListOf(
        stringResource(R.string.filter_chip_all),
        stringResource(R.string.filter_chip_changed),
        stringResource(R.string.filter_chip_disabled),
        stringResource(R.string.filter_chip_enabled)
    )

    // Tab state for filter button
    var tabFilterState by rememberSaveable {
        mutableStateOf(true)
    }


    // TopBar icons state
    var filterIconState by rememberSaveable {
        mutableStateOf(false)
    }
    var searchIconState by remember {
        mutableStateOf(false)
    }

    // Flag change dialog
    val showDialog = remember { mutableStateOf(false) }
    var flagName by remember { mutableStateOf("") }
    var flagValue by remember { mutableStateOf("") }

    // Add flag dialog
    val showAddFlagDialog = remember { mutableStateOf(false) }
    var flagType by rememberSaveable { mutableIntStateOf(0) }
    var flagBoolean by rememberSaveable { mutableIntStateOf(0) }
    var flagAddValue by rememberSaveable { mutableStateOf("") }
    var flagAddName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(searchIconState) {
        if (searchIconState)
            focusRequester.requestFocus()
    }

    val androidPackage = viewModel.androidPackage.collectAsState().value

    // DropDown menu
    var dropDownExpanded by remember { mutableStateOf(false) }
    var selectDropDownExpanded by remember { mutableStateOf(false) }

    // IntFloatStrValues
    val editTextValue = rememberSaveable {
        mutableStateOf("")
    }

    val selectedFlagsType: FlagsTypes = when (pagerState.targetPage) {
        0 -> {
            FlagsTypes.BOOLEAN
        }

        1 -> {
            FlagsTypes.INTEGER
        }

        2 -> {
            FlagsTypes.FLOAT
        }

        else -> {
            FlagsTypes.STRING
        }
    }

    LaunchedEffect(
        pagerState.targetPage
    ) {
        when (pagerState.targetPage) {
            0 -> {
                viewModel.getBoolFlags()
            }

            1 -> {
                viewModel.getIntFlags()
            }

            2 -> {
                viewModel.getFloatFlags()
            }

            3 -> {
                viewModel.getStringFlags()
            }
        }
        viewModel.initAllOverriddenFlagsByPackage(packageName.toString())
    }

    LaunchedEffect(
        viewModel.filterMethod.value,
        viewModel.searchQuery.value
    ) {
        viewModel.getAllFlags()
        viewModel.initAllOverriddenFlagsByPackage(packageName.toString())
    }

    // Share flags dialog
    val showShareDialog = remember { mutableStateOf(false) }
    val fileName = remember { mutableStateOf(packageName ?: "") }

    // SuggestFlagsDialog
    val showSendSuggestDialog = remember { mutableStateOf(false) }
    val suggestFlagDesc = remember { mutableStateOf("") }
    val senderName = remember { mutableStateOf("") }

    // ReportFlagsDialog
    val showSendReportDialog = remember { mutableStateOf(false) }
    val reportFlagDesc = remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = if (isInSelectionMode && viewModel.selectedItems.isNotEmpty()) {
                                stringResource(
                                    R.string.flag_change_topbar_title_selected,
                                    viewModel.selectedItems.size
                                )
                            } else {
                                packageName ?: "Null package name"
                            },
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clipboardManager.setText(AnnotatedString(packageName.toString()))
                                    },
                                    onLongClickLabel = ""
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (searchIconState) searchIconState = false
                                viewModel.initOverriddenBoolFlags(packageName.toString()) // todo
                                viewModel.initOverriddenIntFlags(packageName.toString()) // todo
                                filterIconState = !filterIconState
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = if (filterIconState) Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            else Modifier.background(Color.Transparent)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_filter),
                                contentDescription = "Filter"
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (filterIconState) filterIconState = false
                                searchIconState = !searchIconState
                            },
                            modifier = if (searchIconState) Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            else Modifier.background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Localized description"
                            )
                        }
                        IconButton(
                            onClick = {
                                dropDownExpanded = !dropDownExpanded
                            }
                        ) {
                            FlagChangeDropDown(
                                expanded = dropDownExpanded,
                                onDismissRequest = { dropDownExpanded = false },
                                onAddFlag = {
                                    showAddFlagDialog.value = true
                                },
                                onAddMultipleFlags = {
                                    dropDownExpanded = false
                                    onAddMultipleFlags(packageName.toString())
                                },
                                onDeleteOverriddenFlags = {
                                    dropDownExpanded = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    with(viewModel) {
                                        showFalseProgressDialog()
                                        deleteOverriddenFlagByPackage(packageName = packageName.toString())
                                        resetFilterLists()
                                        initAllFlags()
                                        initAllOverriddenFlagsByPackage(packageName.toString())
                                    }
                                    Log.e("intState", uiStateInteger.value.toString())
                                },
                                onOpenAppDetailsSettings = {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    val uri = Uri.fromParts("package", androidPackage, null)
                                    intent.data = uri
                                    startActivity(context, intent, null)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Localized description"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (isInSelectionMode) {
                            IconButton(onClick = {
                                resetSelectionMode()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Localized description"
                                )
                            }
                        } else {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Localized description"
                                )
                            }
                        }
                    },
                )
                AnimatedVisibility(visible = !isInSelectionMode) {
                    GFlagsTabRow(
                        list = titles,
                        tabState = tabState,
                        topBarState = topBarState,
                        enabled = !isInSelectionMode,
                        onClick = { index ->
                            coroutineScope.launch {
                                pagerState.scrollToPage(index)
                            }
                            tabFilterState = index == 0
                            tabState = index
                        }
                    )
                }
                AnimatedVisibility(visible = filterIconState) {
                    GFlagFilterChipRow(
                        list = chipsList,
                        selectedChips = selectedChips,
                        pagerCurrentState = pagerState.currentPage,
                        colorFraction = FastOutLinearInEasing.transform(topBarState.collapsedFraction),
                        chipOnClick = {
                            when (it) {
                                0 -> viewModel.filterMethod.value = FilterMethod.ALL
                                1 -> viewModel.filterMethod.value = FilterMethod.CHANGED
                                2 -> viewModel.filterMethod.value = FilterMethod.DISABLED
                                3 -> viewModel.filterMethod.value = FilterMethod.ENABLED
                            }
                            selectedChips = it
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                }
                AnimatedVisibility(visible = searchIconState) {
                    GFlagsSearchBar(
                        query = viewModel.searchQuery.value,
                        onQueryChange = { newQuery ->
                            viewModel.searchQuery.value = newQuery
                        },
                        placeHolderText = stringResource(R.string.search_flags_advice),
                        iconVisibility = viewModel.searchQuery.value.isNotEmpty(),
                        iconOnClick = {
                            viewModel.searchQuery.value = ""
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        colorFraction = FastOutLinearInEasing.transform(topBarState.collapsedFraction),
                        keyboardFocus = focusRequester
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isInSelectionMode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { selectDropDownExpanded = !selectDropDownExpanded }) {
                            FlagSelectDropDown(
                                expanded = selectDropDownExpanded,
                                onDismissRequest = { selectDropDownExpanded = false },
                                onEnableSelected = {
                                    viewModel.showProgressDialog.value = true
                                    viewModel.enableSelectedFlag()
                                    viewModel.showFalseProgressDialog(viewModel.selectedItems.size)
                                },
                                onDisableSelected = {
                                    viewModel.showProgressDialog.value = true
                                    viewModel.disableSelectedFlag()
                                    viewModel.showFalseProgressDialog(viewModel.selectedItems.size)
                                },
                                onSelectAllItems = {
                                    viewModel.selectAllItems()
                                })
                            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null)
                        }
                        IconButton(onClick = {
                            showSendReportDialog.value = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }, enabled = true) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_report),
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = {
                            showSendSuggestDialog.value = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }, enabled = true) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_navbar_suggestions_inactive),
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showShareDialog.value = true
                        }, enabled = true) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                viewModel.saveSelectedFlags()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.showProgressDialog.value = true
                                viewModel.showFalseProgressDialog(viewModel.selectedItems.size)
                                coroutineScope.launch {
                                    delay(35)
                                    resetSelectionMode()
                                }
                            },
                            containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save_inactive),
                                "Localized description"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                when (page) {
                    0 -> tabState = 0
                    1 -> tabState = 1
                    2 -> tabState = 2
                    3 -> tabState = 3
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isInSelectionMode,
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding())
        ) { page ->
            when (page) {
                0 -> {
                    BooleanFlagsScreen(
                        uiState = uiStateBoolean.value,
                        viewModel = viewModel,
                        packageName = packageName.toString(),
                        haptic = haptic,
                        savedFlagsList = savedFlags.value,
                        isSelectedList = viewModel.selectedItems,
                        selectedItemLongClick = { isSelected, flagName ->
                            if (isInSelectionMode) {
                                if (isSelected) {
                                    viewModel.selectedItems.remove(flagName)
                                } else {
                                    viewModel.selectedItems.add(flagName)
                                }
                            } else {
                                isInSelectionMode = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.selectedItems.add(flagName)
                            }
                        },
                        selectedItemShortClick = { isSelected, flagName ->
                            if (isInSelectionMode) {
                                if (isSelected) {
                                    viewModel.selectedItems.remove(flagName)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.selectedItems.add(flagName)
                                }
                            }
                        }
                    )
                }

                1 -> OtherTypesFlagsScreen(
                    uiState = uiStateInteger.value,
                    viewModel = viewModel,
                    packageName = packageName.toString(),
                    flagName = flagName,
                    flagValue = flagValue,
                    flagsType = selectedFlagsType,
                    editTextValue = editTextValue.value,
                    showDialog = showDialog.value,
                    onFlagClick = { newFlagName, newFlagValue, newEditTextValue, _ ->
                        flagName = newFlagName
                        flagValue = newFlagValue
                        editTextValue.value = newEditTextValue
                        showDialog.value = true
                    },
                    dialogOnQueryChange = {
                        editTextValue.value = it
                        flagValue = editTextValue.value
                    },
                    dialogOnConfirm = {
                        showDialog.value = false
                    },
                    dialogOnDismiss = {
                        showDialog.value = false
                        editTextValue.value = flagValue
                    },
                    dialogOnDefault = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.resetOtherTypesFlagsToDefault(flagName)
                        viewModel.initIntValues()
                        viewModel.initOverriddenIntFlags(packageName.toString())
                        showDialog.value = false
                    },
                    haptic = haptic,
                    context = context,
                    savedFlagsList = savedFlags.value
                )

                2 -> OtherTypesFlagsScreen(
                    uiState = uiStateFloat.value,
                    viewModel = viewModel,
                    packageName = packageName.toString(),
                    flagName = flagName,
                    flagValue = flagValue,
                    flagsType = selectedFlagsType,
                    editTextValue = editTextValue.value,
                    showDialog = showDialog.value,
                    onFlagClick = { newFlagName, newFlagValue, newEditTextValue, _ ->
                        flagName = newFlagName
                        flagValue = newFlagValue
                        editTextValue.value = newEditTextValue
                        showDialog.value = true
                    },
                    dialogOnQueryChange = {
                        editTextValue.value = it
                        flagValue = editTextValue.value
                    },
                    dialogOnConfirm = {
                        showDialog.value = false
                    },
                    dialogOnDismiss = {
                        showDialog.value = false
                        editTextValue.value = flagValue
                    },
                    dialogOnDefault = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.resetOtherTypesFlagsToDefault(flagName)
                        viewModel.initFloatValues()
                        viewModel.initOverriddenFloatFlags(packageName.toString())
                        showDialog.value = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_flag_value_is_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    haptic = haptic,
                    context = context,
                    savedFlagsList = savedFlags.value
                )

                3 -> OtherTypesFlagsScreen(
                    uiState = uiStateString.value,
                    viewModel = viewModel,
                    packageName = packageName.toString(),
                    flagName = flagName,
                    flagValue = flagValue,
                    flagsType = selectedFlagsType,
                    editTextValue = editTextValue.value,
                    showDialog = showDialog.value,
                    onFlagClick = { newFlagName, newFlagValue, newEditTextValue, _ ->
                        flagName = newFlagName
                        flagValue = newFlagValue
                        editTextValue.value = newEditTextValue
                        showDialog.value = true
                    },
                    dialogOnQueryChange = {
                        editTextValue.value = it
                        flagValue = editTextValue.value
                    },
                    dialogOnConfirm = {
                        showDialog.value = false
                    },
                    dialogOnDismiss = {
                        showDialog.value = false
                        editTextValue.value = flagValue
                    },
                    dialogOnDefault = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.resetOtherTypesFlagsToDefault(flagName)
                        viewModel.initStringValues()
                        viewModel.initOverriddenStringFlags(packageName.toString())
                        showDialog.value = false
                    },
                    haptic = haptic,
                    context = context,
                    savedFlagsList = savedFlags.value
                )
            }
        }

        ShareFlagsDialog(
            showDialog = showShareDialog.value,
            fileName = fileName.value,
            fileNameChange = {
                fileName.value = it
            },
            onTrailingIconClick = {
                fileName.value = ""
            },
            onSend = {

                Toast.makeText(context, "Exporting...", Toast.LENGTH_SHORT).show()
                showShareDialog.value = false

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        packageName?.let {
                            val file = viewModel.extractToFile(fileName.value, it).toFile()
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "ua.polodarb.gmsflags.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                                    putExtra(EXTRA_STREAM, uri)
                                    setFlags(FLAG_ACTIVITY_NEW_TASK)
                                    setType("application/gmsflags")
                                }
                                val chooserIntent = createChooser(intent, null)
                                context.startActivity(chooserIntent)
                            } else {
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "The file was not created",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("file", e.toString())
                    }
                }
            }
        ) {
            showShareDialog.value = false
        }

        SuggestFlagsDialog(
            showDialog = showSendSuggestDialog.value,
            flagDesc = suggestFlagDesc.value,
            onFlagDescChange = {
                suggestFlagDesc.value = it
            },
            senderName = senderName.value,
            onSenderNameChanged = {
                senderName.value = it
            },
            onSend = {
                when (val result = uiStateBoolean.value) {
                    is ua.polodarb.repository.uiStates.UiStates.Success -> {

                        val listBool = result.data.toSortMap()

                        val selectedItemsWithValues =
                            viewModel.selectedItems.mapNotNull { selectedItem ->
                                val value = listBool[selectedItem]
                                if (value != null) {
                                    "$selectedItem: $value"
                                } else {
                                    null
                                }
                            }

                        val flagsText = selectedItemsWithValues.joinToString("\n")

                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("gmsflags@gmail.com"))
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Flags suggestion by ${senderName.value}"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Sender: ${senderName.value}\n\n" +
                                        "Package: ${packageName.toString()}\n\n" +
                                        "Description: \n${suggestFlagDesc.value}\n\n" +
                                        "Flags: \n${flagsText}"
                            )
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(
                                context, "No app to send email. Please install at least one",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showSendSuggestDialog.value = false
                        suggestFlagDesc.value = ""
                        senderName.value = ""
                    }

                    else -> {}
                }
            },
            onDismiss = {
                showSendSuggestDialog.value = false
                suggestFlagDesc.value = ""
                senderName.value = ""
            })

        ReportFlagsDialog(
            showDialog = showSendReportDialog.value,
            flagDesc = reportFlagDesc.value,
            onFlagDescChange = {
                reportFlagDesc.value = it
            },
            onSend = {
                when (val result = uiStateBoolean.value) {
                    is UiStates.Success -> {

                        val listBool = result.data.toSortMap()

                        val selectedItemsWithValues =
                            viewModel.selectedItems.mapNotNull { selectedItem ->
                                val value = listBool[selectedItem]
                                if (value != null) {
                                    "$selectedItem: $value"
                                } else {
                                    null
                                }
                            }

                        val flagsText = selectedItemsWithValues.joinToString("\n")

                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("gmsflags@gmail.com"))
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Problem report"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Package: ${packageName.toString()}\n\n" +
                                        "Description: \n${reportFlagDesc.value}\n\n" +
                                        "Flags: \n${flagsText}"
                            )
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(
                                context, "No app to send email. Please install at least one",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showSendReportDialog.value = false
                        reportFlagDesc.value = ""
                    }

                    else -> {}
                }
            },
            onDismiss = {
                showSendReportDialog.value = false
                reportFlagDesc.value = ""
            })


        ProgressDialog(showDialog = viewModel.showProgressDialog.value)

        AddFlagDialog(
            showDialog = showAddFlagDialog.value,
            flagType = flagType,
            onFlagTypeChange = { flagType = it },
            flagBoolean = flagBoolean,
            onFlagBooleanChange = { flagBoolean = it },
            flagName = flagAddName,
            flagNameChange = { flagAddName = it },
            flagValue = flagAddValue,
            flagValueChange = { flagAddValue = it },
            onAddFlag = {
                coroutineScope.launch {
                    when (flagType) {
                        0 -> {
                            val value = if (flagBoolean == 0) "1" else "0"
                            packageName?.let {
                                viewModel.overrideFlag(
                                    packageName = packageName,
                                    flags = OverriddenFlagsContainer(
                                        boolValues = mapOf(flagAddName to value)
                                    )
                                )
                                viewModel.addManuallyBoolFlag(
                                    flagAddName, value
                                )
                            }
                        }

                        1 -> {
                            packageName?.let {
                                viewModel.overrideFlag(
                                    packageName = packageName.toString(),
                                    flags = OverriddenFlagsContainer(
                                        intValues = mapOf(flagAddName to flagAddValue)
                                    )
                                )
                                viewModel.addManuallyIntFlag(flagAddName, flagAddValue)
                            }
                        }

                        2 -> {
                            packageName?.let {
                                viewModel.overrideFlag(
                                    packageName = packageName.toString(),
                                    flags = OverriddenFlagsContainer(
                                        floatValues = mapOf(flagAddName to flagAddValue)
                                    )
                                )
                                viewModel.addManuallyFloatFlag(flagAddName, flagAddValue)
                            }
                        }

                        3 -> {
                            packageName?.let {
                                viewModel.overrideFlag(
                                    packageName = packageName.toString(),
                                    flags = OverriddenFlagsContainer(
                                        stringValues = mapOf(flagAddName to flagAddValue)
                                    )
                                )
                                viewModel.addManuallyStringFlag(flagAddName, flagAddValue)
                            }
                        }
                    }
                }
                dropDownExpanded = false
                viewModel.filterMethod = viewModel.filterMethod
                viewModel.clearPhenotypeCache(packageName.toString())
                showAddFlagDialog.value = false
                flagAddName = ""
                flagAddValue = ""
            },
            onDismiss = {
                showAddFlagDialog.value = false
                flagAddName = ""
                flagAddValue = ""
            }
        )
    }
}

enum class SelectFlagsType {
    BOOLEAN, INTEGER, FLOAT, STRING
}
