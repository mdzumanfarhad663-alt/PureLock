package com.example.purelock.ui

import android.app.Application
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purelock.data.PureLockState
import com.example.purelock.data.SessionManager
import com.example.purelock.vpn.PureLockVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PureLockViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    val state: StateFlow<PureLockState> = sessionManager.state

    private val _remainingFormatted = MutableStateFlow("00:00:00")
    val remainingFormatted: StateFlow<String> = _remainingFormatted.asStateFlow()

    private val _endTimeFormatted = MutableStateFlow("")
    val endTimeFormatted: StateFlow<String> = _endTimeFormatted.asStateFlow()

    private val _isCheckingInternet = MutableStateFlow(false)
    val isCheckingInternet: StateFlow<Boolean> = _isCheckingInternet.asStateFlow()

    private val _internetWorking = MutableStateFlow(true)
    val internetWorking: StateFlow<Boolean> = _internetWorking.asStateFlow()

    init {
        startTimerLoop()
        checkInternet()
    }

    private fun startTimerLoop() {
        viewModelScope.launch {
            while (true) {
                sessionManager.checkAndCleanExpiredSession()
                updateFormattedTimes(state.value)
                delay(1000)
            }
        }
    }

    private fun updateFormattedTimes(currentState: PureLockState) {
        val remaining = currentState.remainingMillis
        if (remaining <= 0) {
            _remainingFormatted.value = "00:00:00"
            _endTimeFormatted.value = ""
            return
        }

        val totalSecs = remaining / 1000
        val days = totalSecs / 86400
        val hours = (totalSecs % 86400) / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60

        _remainingFormatted.value = if (days > 0) {
            String.format(Locale.US, "%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
        }

        val endTime = when {
            currentState.isStrictActive -> currentState.strictEndTimeMillis
            currentState.isTestActive -> currentState.testEndTimeMillis
            else -> 0L
        }

        if (endTime > 0) {
            val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault())
            _endTimeFormatted.value = sdf.format(Date(endTime))
        } else {
            _endTimeFormatted.value = ""
        }
    }

    fun needsVpnPermission(): Boolean {
        return VpnService.prepare(getApplication()) != null
    }

    fun enableStrictProtection(durationMillis: Long) {
        sessionManager.startStrictSession(durationMillis)
        PureLockVpnService.startService(getApplication())
    }

    fun enableTestProtection(durationMinutes: Int) {
        sessionManager.startTestSession(durationMinutes)
        PureLockVpnService.startService(getApplication())
    }

    fun stopTestProtection() {
        sessionManager.stopTestSession()
        PureLockVpnService.stopTestService(getApplication())
    }

    fun checkInternet() {
        viewModelScope.launch {
            _isCheckingInternet.value = true
            val working = withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName("1.1.1.1")
                    address.isReachable(2000)
                } catch (e: Exception) {
                    false
                }
            }
            _internetWorking.value = working
            _isCheckingInternet.value = false
        }
    }
}
