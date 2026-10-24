package com.bsfreetv.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsfreetv.launcher.domain.home.AdbStep
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.system.AdbOutcome
import com.bsfreetv.launcher.system.HomeControl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The self-ADB jobs the Default launcher page can start; named in ui/Labels.kt. */
enum class SetupTask { MAKE_HOME, RESTORE, ALLOW_NEXT_UP, ALLOW_RESTRICTED, TEST }

sealed interface SetupState {
    data object Idle : SetupState
    /** Waiting for adb; the TV may be showing "Allow debugging?". */
    data class Working(val task: SetupTask) : SetupState
    data class Result(val task: SetupTask, val outcome: AdbOutcome) : SetupState
}

@HiltViewModel
class HomeSetupViewModel @Inject constructor(private val control: HomeControl) : ViewModel() {
    private val _status = MutableStateFlow(control.status())
    val status: StateFlow<HomeStatus> = _status.asStateFlow()
    private val _state = MutableStateFlow<SetupState>(SetupState.Idle)
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun refresh() { _status.value = control.status() }

    fun makeDefault() = run(SetupTask.MAKE_HOME) { HomeCommands.makeDefault(it, control.homeActivity) }

    fun restoreStock() = run(SetupTask.RESTORE) { HomeCommands.restoreStock(it) }

    fun grantNextUp() = run(SetupTask.ALLOW_NEXT_UP) { listOf(HomeCommands.grantTvListings(it.selfPackage)) }

    fun allowRestricted() = run(SetupTask.ALLOW_RESTRICTED) { listOf(HomeCommands.allowRestrictedSettings(it.selfPackage)) }

    fun openAccessibilitySettings() = control.openAccessibilitySettings()

    fun openAppInfo() = control.openAppInfo()

    fun probe() {
        if (_state.value is SetupState.Working) return
        _state.value = SetupState.Working(SetupTask.TEST)
        viewModelScope.launch {
            val outcome = control.probe()
            _state.value = SetupState.Result(SetupTask.TEST, outcome)
        }
    }

    fun pcCommands(): List<String> = HomeCommands.forPc(HomeCommands.makeDefault(status.value, control.homeActivity))

    fun homeActivity() = control.homeActivity

    private fun run(task: SetupTask, steps: (HomeStatus) -> List<AdbStep>) {
        if (_state.value is SetupState.Working) return
        refresh()
        val plan = steps(_status.value)
        _state.value = SetupState.Working(task)
        viewModelScope.launch {
            val outcome = control.run(plan)
            refresh()
            _state.value = SetupState.Result(task, outcome)
        }
    }
}
