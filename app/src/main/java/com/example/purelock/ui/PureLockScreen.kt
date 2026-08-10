package com.example.purelock.ui

import com.example.purelock.data.PureLockState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.purelock.ui.components.CustomDateTimePickerDialog
import com.example.purelock.ui.components.StrictConfirmationDialog
import com.example.ui.theme.AlertRose
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryCyanGlow
import com.example.ui.theme.PureDarkBackground
import com.example.ui.theme.PureDarkBorder
import com.example.ui.theme.PureDarkSurface
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class DurationOption(val label: String, val durationMillis: Long) {
    ONE_DAY("1 Day", 24 * 60 * 60 * 1000L),
    TWO_DAYS("2 Days", 2 * 24 * 60 * 60 * 1000L),
    THREE_DAYS("3 Days", 3 * 24 * 60 * 60 * 1000L),
    SEVEN_DAYS("7 Days", 7 * 24 * 60 * 60 * 1000L),
    THIRTY_DAYS("30 Days", 30L * 24 * 60 * 60 * 1000L),
    CUSTOM("Custom", 0L)
}

@Composable
fun PureLockScreen(
    viewModel: PureLockViewModel,
    onRequestVpnPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val remainingFormatted by viewModel.remainingFormatted.collectAsState()
    val endTimeFormatted by viewModel.endTimeFormatted.collectAsState()
    val isCheckingInternet by viewModel.isCheckingInternet.collectAsState()
    val internetWorking by viewModel.internetWorking.collectAsState()

    var selectedOption by remember { mutableStateOf(DurationOption.ONE_DAY) }
    var customDurationMillis by remember { mutableStateOf(0L) }

    var showStrictConfirmDialog by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }

    val activeDurationMillis = if (selectedOption == DurationOption.CUSTOM) {
        customDurationMillis
    } else {
        selectedOption.durationMillis
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureDarkBackground)
            .padding(20.dp)
    ) {
        when {
            state.isStrictActive -> {
                StrictActiveContent(
                    remainingFormatted = remainingFormatted,
                    endTimeFormatted = endTimeFormatted
                )
            }

            state.isTestActive -> {
                TestActiveContent(
                    remainingFormatted = remainingFormatted,
                    endTimeFormatted = endTimeFormatted,
                    state = state,
                    isCheckingInternet = isCheckingInternet,
                    internetWorking = internetWorking,
                    onStopTest = { viewModel.stopTestProtection() },
                    onRefreshInternet = { viewModel.checkInternet() }
                )
            }

            else -> {
                InactiveSetupContent(
                    selectedOption = selectedOption,
                    customDurationMillis = customDurationMillis,
                    onOptionSelected = { option ->
                        if (option == DurationOption.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            selectedOption = option
                        }
                    },
                    onEnableProtection = {
                        if (viewModel.needsVpnPermission()) {
                            onRequestVpnPermission()
                        } else {
                            showStrictConfirmDialog = true
                        }
                    },
                    onStartTestMode = { minutes ->
                        if (viewModel.needsVpnPermission()) {
                            onRequestVpnPermission()
                        } else {
                            viewModel.enableTestProtection(minutes)
                        }
                    }
                )
            }
        }

        // Dialogs
        if (showStrictConfirmDialog) {
            val label = if (selectedOption == DurationOption.CUSTOM) {
                val hours = customDurationMillis / (1000 * 3600)
                "Custom ($hours hours)"
            } else {
                selectedOption.label
            }

            val calcEndTime = System.currentTimeMillis() + activeDurationMillis
            val sdf = java.text.SimpleDateFormat("EEE, MMM d, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
            val formattedEnd = sdf.format(java.util.Date(calcEndTime))

            StrictConfirmationDialog(
                durationText = label,
                endTimeText = formattedEnd,
                onConfirm = {
                    showStrictConfirmDialog = false
                    viewModel.enableStrictProtection(activeDurationMillis)
                },
                onDismiss = { showStrictConfirmDialog = false }
            )
        }

        if (showCustomPicker) {
            CustomDateTimePickerDialog(
                onDateTimeSelected = { millis ->
                    customDurationMillis = millis
                    selectedOption = DurationOption.CUSTOM
                    showCustomPicker = false
                },
                onDismiss = { showCustomPicker = false }
            )
        }
    }
}

@Composable
private fun StrictActiveContent(
    remainingFormatted: String,
    endTimeFormatted: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Shield Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryCyanGlow.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Shield Active",
                tint = PrimaryCyan,
                modifier = Modifier.size(68.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🛡️ PROTECTION ACTIVE",
            color = PrimaryCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = AlertRose,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "STRICT MODE 🔒",
                color = AlertRose,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Countdown Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureDarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TIME REMAINING",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = remainingFormatted,
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("strict_countdown_text")
                )

                if (endTimeFormatted.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ends:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = endTimeFormatted,
                        color = PrimaryCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = PureDarkSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Blocked",
                    tint = SecondaryEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Adult websites are blocked.",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Strict Mode is active. Normal internet works continuously. Settings, filters, and duration cannot be changed until expiry.",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun TestActiveContent(
    remainingFormatted: String,
    endTimeFormatted: String,
    state: PureLockState,
    isCheckingInternet: Boolean,
    internetWorking: Boolean,
    onStopTest: () -> Unit,
    onRefreshInternet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Test Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = "Test Mode",
                tint = PrimaryCyan
            )
            Text(
                text = "TEST MODE ACTIVE",
                color = PrimaryCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = remainingFormatted,
            color = TextPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("test_countdown_text")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // STOP TEST PROTECTION BUTTON
        Button(
            onClick = onStopTest,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertRose,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("stop_test_button")
        ) {
            Text(
                text = "STOP TEST PROTECTION",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Status Metrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureDarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PureDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "LIVE TEST DIAGNOSTICS",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                StatusRow(
                    label = "VPN",
                    value = if (state.isProtectionActive) "Connected" else "Disconnected",
                    isGood = state.isProtectionActive
                )

                StatusRow(
                    label = "DNS Filter",
                    value = if (state.isProtectionActive) "Active" else "Inactive",
                    isGood = state.isProtectionActive
                )

                StatusRow(
                    label = "Provider",
                    value = state.activeProvider,
                    isGood = true
                )

                StatusRow(
                    label = "DNS Server",
                    value = state.activeDnsServer,
                    isGood = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Internet", color = TextSecondary, fontSize = 14.sp)
                        Text(
                            text = if (internetWorking) "Working" else "Checking/Limited",
                            color = if (internetWorking) SecondaryEmerald else AlertRose,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onRefreshInternet,
                        enabled = !isCheckingInternet
                    ) {
                        if (isCheckingInternet) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Check Connection",
                                tint = PrimaryCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(
            text = value,
            color = if (isGood) TextPrimary else AlertRose,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InactiveSetupContent(
    selectedOption: DurationOption,
    customDurationMillis: Long,
    onOptionSelected: (DurationOption) -> Unit,
    onEnableProtection: () -> Unit,
    onStartTestMode: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "PureLock",
                tint = PrimaryCyan,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "PureLock",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Automatic adult website blocker with DNS-only filtering.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Duration Selector Label
        Text(
            text = "SELECT LOCK DURATION",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Options Grid (1 Day, 2 Days, 3 Days, 7 Days, 30 Days, Custom)
        val options = listOf(
            DurationOption.ONE_DAY,
            DurationOption.TWO_DAYS,
            DurationOption.THREE_DAYS,
            DurationOption.SEVEN_DAYS,
            DurationOption.THIRTY_DAYS,
            DurationOption.CUSTOM
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (chunk in options.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (opt in chunk) {
                        val isSelected = selectedOption == opt
                        val label = if (opt == DurationOption.CUSTOM && customDurationMillis > 0) {
                            val h = customDurationMillis / (1000 * 3600)
                            "Custom (${h}h)"
                        } else {
                            opt.label
                        }

                        Surface(
                            onClick = { onOptionSelected(opt) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("duration_option_${opt.name}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryCyan.copy(alpha = 0.15f) else PureDarkSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) PrimaryCyan else PureDarkBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (opt == DurationOption.CUSTOM) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Custom Date",
                                        tint = if (isSelected) PrimaryCyan else TextSecondary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) PrimaryCyan else TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Enable Protection Primary Button
        Button(
            onClick = onEnableProtection,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = PureDarkBackground
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("enable_protection_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Enable Protection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Test Mode Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureDarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PureDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Test Mode",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Test Mode",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Test adult filtering with a short session that can be stopped anytime.",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onStartTestMode(1) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_mode_1min_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("1 Minute", color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = { onStartTestMode(5) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_mode_5min_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("5 Minutes", color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
