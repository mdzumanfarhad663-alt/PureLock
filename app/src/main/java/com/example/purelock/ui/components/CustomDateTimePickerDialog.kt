package com.example.purelock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PureDarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateTimePickerDialog(
    onDateTimeSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Date, 2: Time

    val calendar = remember { Calendar.getInstance() }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calendar.timeInMillis
    )

    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = false
    )

    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (step == 1) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(
                    onClick = {
                        if (datePickerState.selectedDateMillis != null) {
                            step = 2
                        }
                    },
                    modifier = Modifier.testTag("next_time_picker_button")
                ) {
                    Text("Next: Pick Time")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = PureDarkSurface,
            title = {
                Text(
                    text = "Pick Lock End Time",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState)

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val c = Calendar.getInstance().apply {
                            timeInMillis = selectedDateMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val futureMillis = c.timeInMillis
                        val duration = futureMillis - System.currentTimeMillis()

                        if (duration <= 60000) { // must be at least 1 min in future
                            errorMessage = "Please select a time at least 1 minute in the future."
                        } else {
                            onDateTimeSelected(duration)
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan
                    ),
                    modifier = Modifier.testTag("confirm_time_button")
                ) {
                    Text("Set Custom Time")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { step = 1 }) {
                    Text("Back to Date")
                }
            }
        )
    }
}
