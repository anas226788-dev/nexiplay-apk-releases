package com.nexiplay.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.repository.SettingsRepository
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var hideNsfw by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var whatsappNumber by remember { mutableStateOf<String?>(null) }
    var storageUriStr by remember { mutableStateOf(settings.sdCardUri) }

    val appUpdateInfo by com.nexiplay.app.data.util.AppUpdateManager.updateInfo.collectAsState()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showManualUpdateDialog by remember { mutableStateOf(false) }

    val documentTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                settings.sdCardUri = uri.toString()
                storageUriStr = uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        val user = SupabaseClient.main.auth.currentUserOrNull()
        isLoggedIn = user != null
        if (user != null) {
            userId = user.id
            try {
                val profile = SupabaseClient.main.from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()
                hideNsfw = profile?.get("hide_nsfw")?.toString() == "true"
                
                val meta = user.userMetadata
                val metaWhatsapp = meta?.get("whatsapp_number")?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content.takeIf { c -> c != "null" } else it.toString().replace("\"", "").takeIf { c -> c != "null" }
                }
                val dbWhatsapp = profile?.get("whatsapp_number")?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content.takeIf { c -> c != "null" } else it.toString().replace("\"", "").takeIf { c -> c != "null" }
                }
                whatsappNumber = dbWhatsapp?.takeIf { it.isNotBlank() && it != "null" } ?: metaWhatsapp
            } catch (e: Exception) { 
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = themeTextPrimary()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface()),
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Account & Security ──
            if (isLoggedIn) {
                Text("Account & Security", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeCard(), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SettingsClickableValueRow("WhatsApp Number", whatsappNumber ?: "Not set") { showWhatsAppDialog = true }
                    Divider(color = themeSurface())
                    SettingsClickableRow("Change Email") { showChangeEmailDialog = true }
                    Divider(color = themeSurface())
                    SettingsClickableRow("Change Password") { showChangePasswordDialog = true }
                }
                
                Spacer(Modifier.height(24.dp))
            }

            // ── Content Settings ──
            Text("Content", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeCard(), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hide NSFW Content", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary())
                    Text("Hide 18+ content from browse and search", fontSize = 11.sp, color = themeTextTertiary())
                }
                Switch(
                    checked = hideNsfw,
                    onCheckedChange = { checked ->
                        hideNsfw = checked
                        if (isLoggedIn) {
                            scope.launch {
                                try {
                                    SupabaseClient.main.from("profiles").update(
                                        mapOf("hide_nsfw" to checked)
                                    ) { filter { eq("id", userId) } }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to update setting: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexiRed),
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // ── Storage Settings ──
            Text("Downloads", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeCard(), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Download Location", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary())
                        val locText = if (storageUriStr != null) {
                            try {
                                val df = DocumentFile.fromTreeUri(context, Uri.parse(storageUriStr!!))
                                df?.name ?: "Unknown Folder"
                            } catch (e: Exception) { "Unknown Folder" }
                        } else {
                            "Internal Storage"
                        }
                        Text(locText, fontSize = 11.sp, color = themeTextTertiary())
                    }
                    Button(
                        onClick = { documentTreeLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeSurface()),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Change", color = NexiRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (storageUriStr != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            settings.sdCardUri = null
                            storageUriStr = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = themeTextPrimary())
                    ) {
                        Text("Reset to Internal Storage", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── About ──
            Text("About", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeCard(), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                SettingsRow("App", "NexiPlay")
                SettingsRow("Version", "${com.nexiplay.app.BuildConfig.VERSION_NAME} (${com.nexiplay.app.BuildConfig.VERSION_CODE})")
                SettingsClickableRow(if (isCheckingUpdate) "Checking for updates..." else "Check for Updates") {
                    if (!isCheckingUpdate) {
                        isCheckingUpdate = true
                        scope.launch {
                            com.nexiplay.app.data.util.AppUpdateManager.checkForUpdate()
                            isCheckingUpdate = false
                            if (com.nexiplay.app.data.util.AppUpdateManager.isUpdateAvailable) {
                                showManualUpdateDialog = true
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "You are using the latest version (v${com.nexiplay.app.BuildConfig.VERSION_NAME})! 🎉",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                SettingsRow("Developer", "NexiPlay Team")
                SettingsRow("Platform", "Android")
                
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = themeSurface())
                
                SettingsClickableRow("Privacy Policy") {
                    val url = java.net.URLEncoder.encode("https://nexiplay.vercel.app/privacy-policy", "UTF-8")
                    navController.navigate("webview/$url")
                }
                SettingsClickableRow("Terms of Service") {
                    val url = java.net.URLEncoder.encode("https://nexiplay.vercel.app/terms", "UTF-8")
                    navController.navigate("webview/$url")
                }
                SettingsClickableRow("DMCA") {
                    val url = java.net.URLEncoder.encode("https://nexiplay.vercel.app/dmca", "UTF-8")
                    navController.navigate("webview/$url")
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Manual Update Dialog ──
        if (showManualUpdateDialog && appUpdateInfo != null) {
            com.nexiplay.app.ui.components.UpdateDialog(
                updateInfo = appUpdateInfo!!,
                onDismiss = {
                    showManualUpdateDialog = false
                    com.nexiplay.app.data.util.AppUpdateManager.resetState()
                }
            )
        }

        // ── Change Email Dialog ──
        if (showChangeEmailDialog) {
            var newEmail by remember { mutableStateOf("") }
            var isUpdating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isUpdating) showChangeEmailDialog = false },
                containerColor = themeSurface(),
                titleContentColor = themeTextPrimary(),
                textContentColor = themeTextSecondary(),
                title = { Text("Change Email", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter your new email address. You will receive a confirmation link.", fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            placeholder = { Text("New email address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = themeTextPrimary(), unfocusedTextColor = themeTextPrimary()
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newEmail.isBlank()) return@TextButton
                            scope.launch {
                                isUpdating = true
                                try {
                                    SupabaseClient.main.auth.updateUser { email = newEmail.trim() }
                                    android.widget.Toast.makeText(context, "Confirmation email sent to new address!", android.widget.Toast.LENGTH_LONG).show()
                                    showChangeEmailDialog = false
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, e.message ?: "Failed to update email", android.widget.Toast.LENGTH_LONG).show()
                                } finally { isUpdating = false }
                            }
                        },
                        enabled = !isUpdating
                    ) { Text(if (isUpdating) "Updating..." else "Update", color = NexiRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeEmailDialog = false }, enabled = !isUpdating) { Text("Cancel", color = themeTextSecondary()) }
                }
            )
        }

        // "?"? Change Password Dialog "?"?
        if (showChangePasswordDialog) {
            var newPassword by remember { mutableStateOf("") }
            var isUpdating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isUpdating) showChangePasswordDialog = false },
                containerColor = themeSurface(),
                titleContentColor = themeTextPrimary(),
                textContentColor = themeTextSecondary(),
                title = { Text("Change Password", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter your new password (minimum 6 characters).", fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = { Text("New password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = themeTextPrimary(), unfocusedTextColor = themeTextPrimary()
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPassword.length < 6) {
                                android.widget.Toast.makeText(context, "Password must be at least 6 characters", android.widget.Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            scope.launch {
                                isUpdating = true
                                try {
                                    SupabaseClient.main.auth.updateUser { password = newPassword }
                                    android.widget.Toast.makeText(context, "Password updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    showChangePasswordDialog = false
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, e.message ?: "Failed to update password", android.widget.Toast.LENGTH_LONG).show()
                                } finally { isUpdating = false }
                            }
                        },
                        enabled = !isUpdating
                    ) { Text(if (isUpdating) "Updating..." else "Update", color = NexiRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePasswordDialog = false }, enabled = !isUpdating) { Text("Cancel", color = themeTextSecondary()) }
                }
            )
        }

        // ── WhatsApp Dialog ──
        if (showWhatsAppDialog) {
            var inputPhone by remember { mutableStateOf(whatsappNumber ?: "") }
            var isUpdating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isUpdating) showWhatsAppDialog = false },
                containerColor = themeSurface(),
                titleContentColor = themeTextPrimary(),
                textContentColor = themeTextSecondary(),
                title = { Text("WhatsApp Number", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter your WhatsApp number for support, VIP perks & updates.", fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = { inputPhone = it },
                            placeholder = { Text("+88017xxxxxxxx or 017xxxxxxxx") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = themeTextPrimary(), unfocusedTextColor = themeTextPrimary()
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = inputPhone.trim()
                            scope.launch {
                                isUpdating = true
                                try {
                                    SupabaseClient.main.auth.updateUser {
                                        data = kotlinx.serialization.json.buildJsonObject {
                                            put("whatsapp_number", kotlinx.serialization.json.JsonPrimitive(trimmed))
                                        }
                                    }
                                    SupabaseClient.main.from("profiles").update(
                                        mapOf(
                                            "whatsapp_number" to trimmed,
                                            "updated_at" to java.time.Instant.now().toString()
                                        )
                                    ) { filter { eq("id", userId) } }

                                    whatsappNumber = trimmed.ifEmpty { null }
                                    android.widget.Toast.makeText(context, "WhatsApp number updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    showWhatsAppDialog = false
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, e.message ?: "Failed to update WhatsApp number", android.widget.Toast.LENGTH_LONG).show()
                                } finally { isUpdating = false }
                            }
                        },
                        enabled = !isUpdating
                    ) { Text(if (isUpdating) "Saving..." else "Save", color = NexiRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showWhatsAppDialog = false }, enabled = !isUpdating) { Text("Cancel", color = themeTextSecondary()) }
                }
            )
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary())
        Text(value, fontSize = 13.sp, color = themeTextSecondary())
    }
}

@Composable
private fun SettingsClickableRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary())
        Icon(Icons.Default.ChevronRight, contentDescription = "View", tint = themeTextSecondary(), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsClickableValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary())
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, color = if (value == "Not set") themeTextTertiary() else NexiRed, fontFamily = InterFont)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = themeTextSecondary(), modifier = Modifier.size(16.dp))
    }
}
