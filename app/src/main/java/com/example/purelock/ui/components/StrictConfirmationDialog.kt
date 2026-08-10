package com.example.purelock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertRose
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PureDarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StrictConfirmationDialog(
    durationText: String,
    endTimeText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureDarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Strict Mode",
                    tint = PrimaryCyan
                )
                Text(
                    text = "Confirm Strict Mode",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Duration: $durationText",
                    color = PrimaryCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (endTimeText.isNotEmpty()) {
                    Text(
                        text = "Ends: $endTimeText",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔒 Strict Mode Rules:",
                            color = AlertRose,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Protection CANNOT be stopped or paused.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Timer CANNOT be shortened once started.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Adult websites will be automatically blocked by DNS.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_strict_button")
            ) {
                Text("Confirm & Start Lock", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
