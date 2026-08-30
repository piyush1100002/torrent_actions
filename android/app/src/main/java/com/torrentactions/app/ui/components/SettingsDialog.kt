package com.torrentactions.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.torrentactions.app.ui.theme.BackgroundDark
import com.torrentactions.app.ui.theme.NeonCyan
import com.torrentactions.app.ui.theme.NeonEmerald
import com.torrentactions.app.ui.theme.NeonRose
import com.torrentactions.app.ui.theme.SurfaceBorder
import com.torrentactions.app.ui.theme.SurfaceCard
import com.torrentactions.app.ui.theme.TextMuted
import com.torrentactions.app.ui.theme.TextPrimary
import com.torrentactions.app.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    initialToken: String,
    initialOwner: String,
    initialRepo: String,
    initialWorkflow: String,
    onDismiss: () -> Unit,
    onSave: (token: String, owner: String, repo: String, workflow: String) -> Unit,
    onTestConnection: suspend (token: String, owner: String, repo: String) -> Result<String>
) {
    var token by remember { mutableStateOf(initialToken) }
    var owner by remember { mutableStateOf(initialOwner) }
    var repo by remember { mutableStateOf(initialRepo) }
    var workflow by remember { mutableStateOf(initialWorkflow) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Empty
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val openGitHubLogin: () -> Unit = {
        val url = "https://github.com/settings/tokens/new?scopes=repo,workflow&description=Torrent+Actions"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "GitHub Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "The smoothest option is to open GitHub in your browser, generate a PAT with repo/workflow access, then paste it here. This avoids app registration and works reliably on Android.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedButton(
                    onClick = openGitHubLogin,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open GitHub Login", fontSize = 13.sp)
                }

                // Token
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; testResult = null },
                    label = { Text("Personal Access Token (PAT)") },
                    placeholder = { Text("ghp_xxxxxxxxxxxxxxx") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = NeonCyan)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Repo Owner
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it; testResult = null },
                    label = { Text("Repository Owner") },
                    placeholder = { Text("e.g. piyushpradhan00001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Repo Name
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it; testResult = null },
                    label = { Text("Repository Name") },
                    placeholder = { Text("e.g. torrent_actions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Workflow File
                OutlinedTextField(
                    value = workflow,
                    onValueChange = { workflow = it },
                    label = { Text("Workflow File") },
                    placeholder = { Text("torrent_download.yml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Test Connection Button & Status
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isTesting = true
                            testResult = onTestConnection(token.trim(), owner.trim(), repo.trim())
                            isTesting = false
                        }
                    },
                    enabled = token.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection", fontSize = 13.sp)
                    }
                }

                if (testResult != null) {
                    val res = testResult!!
                    if (res.isSuccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = res.getOrNull().orEmpty(),
                                fontSize = 12.sp,
                                color = NeonEmerald,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = NeonRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = res.exceptionOrNull()?.message ?: "Connection failed",
                                fontSize = 12.sp,
                                color = NeonRose,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(token.trim(), owner.trim(), repo.trim(), workflow.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    )
}
