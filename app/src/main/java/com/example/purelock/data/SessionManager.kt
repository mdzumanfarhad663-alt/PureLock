package com.example.purelock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PureLockState(
    val isStrictActive: Boolean = false,
    val strictEndTimeMillis: Long = 0L,
    val isTestActive: Boolean = false,
    val testEndTimeMillis: Long = 0L,
    val activeProvider: String = "CleanBrowsing Family Filter",
    val connectivityStatus: String = "Connected",
    val activeDnsServer: String = "185.228.168.168"
) {
    val isProtectionActive: Boolean
        get() = (isStrictActive && System.currentTimeMillis() < strictEndTimeMillis) ||
                (isTestActive && System.currentTimeMillis() < testEndTimeMillis)

    val remainingMillis: Long
        get() {
            val now = System.currentTimeMillis()
            return when {
                isStrictActive && now < strictEndTimeMillis -> strictEndTimeMillis - now
                isTestActive && now < testEndTimeMillis -> testEndTimeMillis - now
                else -> 0L
            }
        }
}

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<PureLockState> = _state.asStateFlow()

    init {
        checkAndCleanExpiredSession()
    }

    private fun loadState(): PureLockState {
        val isStrict = prefs.getBoolean(KEY_IS_STRICT, false)
        val strictEnd = prefs.getLong(KEY_STRICT_END, 0L)
        val isTest = prefs.getBoolean(KEY_IS_TEST, false)
        val testEnd = prefs.getLong(KEY_TEST_END, 0L)
        val provider = prefs.getString(KEY_PROVIDER, "CleanBrowsing Family Filter") ?: "CleanBrowsing Family Filter"
        val status = prefs.getString(KEY_STATUS, "Connected") ?: "Connected"
        val dns = prefs.getString(KEY_DNS, "185.228.168.168") ?: "185.228.168.168"

        val now = System.currentTimeMillis()
        val validStrict = isStrict && now < strictEnd
        val validTest = isTest && now < testEnd

        return PureLockState(
            isStrictActive = validStrict,
            strictEndTimeMillis = if (validStrict) strictEnd else 0L,
            isTestActive = validTest,
            testEndTimeMillis = if (validTest) testEnd else 0L,
            activeProvider = provider,
            connectivityStatus = status,
            activeDnsServer = dns
        )
    }

    fun checkAndCleanExpiredSession(): Boolean {
        val current = _state.value
        val now = System.currentTimeMillis()
        var changed = false

        var isStrict = current.isStrictActive
        var strictEnd = current.strictEndTimeMillis
        var isTest = current.isTestActive
        var testEnd = current.testEndTimeMillis

        if (isStrict && now >= strictEnd) {
            isStrict = false
            strictEnd = 0L
            changed = true
        }

        if (isTest && now >= testEnd) {
            isTest = false
            testEnd = 0L
            changed = true
        }

        if (changed) {
            prefs.edit()
                .putBoolean(KEY_IS_STRICT, isStrict)
                .putLong(KEY_STRICT_END, strictEnd)
                .putBoolean(KEY_IS_TEST, isTest)
                .putLong(KEY_TEST_END, testEnd)
                .apply()

            _state.value = current.copy(
                isStrictActive = isStrict,
                strictEndTimeMillis = strictEnd,
                isTestActive = isTest,
                testEndTimeMillis = testEnd
            )
        }
        return changed
    }

    fun startStrictSession(durationMillis: Long) {
        val now = System.currentTimeMillis()
        val endTime = now + durationMillis

        prefs.edit()
            .putBoolean(KEY_IS_STRICT, true)
            .putLong(KEY_STRICT_END, endTime)
            .putBoolean(KEY_IS_TEST, false)
            .putLong(KEY_TEST_END, 0L)
            .putString(KEY_PROVIDER, "CleanBrowsing Family Filter")
            .putString(KEY_DNS, "185.228.168.168")
            .apply()

        _state.value = PureLockState(
            isStrictActive = true,
            strictEndTimeMillis = endTime,
            isTestActive = false,
            testEndTimeMillis = 0L,
            activeProvider = "CleanBrowsing Family Filter",
            connectivityStatus = "Connected",
            activeDnsServer = "185.228.168.168"
        )
    }

    fun startTestSession(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        val endTime = now + (durationMinutes * 60 * 1000L)

        prefs.edit()
            .putBoolean(KEY_IS_STRICT, false)
            .putLong(KEY_STRICT_END, 0L)
            .putBoolean(KEY_IS_TEST, true)
            .putLong(KEY_TEST_END, endTime)
            .putString(KEY_PROVIDER, "CleanBrowsing Family Filter")
            .putString(KEY_DNS, "185.228.168.168")
            .apply()

        _state.value = PureLockState(
            isStrictActive = false,
            strictEndTimeMillis = 0L,
            isTestActive = true,
            testEndTimeMillis = endTime,
            activeProvider = "CleanBrowsing Family Filter",
            connectivityStatus = "Connected",
            activeDnsServer = "185.228.168.168"
        )
    }

    fun stopTestSession() {
        prefs.edit()
            .putBoolean(KEY_IS_TEST, false)
            .putLong(KEY_TEST_END, 0L)
            .apply()

        _state.value = _state.value.copy(
            isTestActive = false,
            testEndTimeMillis = 0L
        )
    }

    fun updateStatus(provider: String, status: String, dns: String) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_STATUS, status)
            .putString(KEY_DNS, dns)
            .apply()

        _state.value = _state.value.copy(
            activeProvider = provider,
            connectivityStatus = status,
            activeDnsServer = dns
        )
    }

    companion object {
        private const val PREFS_NAME = "purelock_session_prefs"
        private const val KEY_IS_STRICT = "is_strict_active"
        private const val KEY_STRICT_END = "strict_end_time"
        private const val KEY_IS_TEST = "is_test_active"
        private const val KEY_TEST_END = "test_end_time"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_STATUS = "connectivity_status"
        private const val KEY_DNS = "active_dns"
    }
}
