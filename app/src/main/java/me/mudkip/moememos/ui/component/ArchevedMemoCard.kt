package me.mudkip.moememos.ui.component

import android.app.AlertDialog
import android.text.format.DateUtils
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.viewmodel.LocalArchivedMemos
import me.mudkip.moememos.viewmodel.LocalMemos
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


@Composable
fun ArchivedMemoCard(
    memo: Memo
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 15.dp, vertical = 10.dp)
            .fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 15.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.getRelativeTimeSpanString(memo.createdTs * 1000, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.weight(1f))
                ArchivedMemosCardActionButton(memo)
            }

            MemoContent(memo, previewMode = true)
        }
    }
}

@Composable
fun ArchivedMemosCardActionButton(
    memo: Memo
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val archivedMemoListViewModel = LocalArchivedMemos.current
    val memosViewModel = LocalMemos.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier
        .fillMaxSize()
        .wrapContentSize(Alignment.TopEnd)) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(R.string.restore.string) },
                onClick = {
                    scope.launch {
                        archivedMemoListViewModel.restoreMemo(memo.id).suspendOnSuccess {
                            menuExpanded = false
                            memosViewModel.loadMemos()
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Restore,
                        contentDescription = null
                    )
                })

            var isDialogOpen by remember { mutableStateOf(false) }
            var password by remember { mutableStateOf(TextFieldValue()) }
            var decryptedText by remember { mutableStateOf("") }

            if (isDialogOpen) {
                AlertDialog(
                    onDismissRequest = {
                        isDialogOpen = false
                    },
                    title = {
                        Text("Decrypt memo")
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = password,
                                label = { Text("Password") },
                                onValueChange = {
                                    password = it
                                },
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (password.text.isNotEmpty() && memo.content.isNotEmpty()) {
                                    try {
                                        val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
                                        val secretKey = SecretKeySpec(password.text.toByteArray(Charsets.UTF_8), "AES")
                                        cipher.init(Cipher.DECRYPT_MODE, secretKey)
                                        val memoContent = Base64.decode(memo.content, Base64.DEFAULT)
                                        decryptedText = String(cipher.doFinal(memoContent))
                                    } catch (e: Exception) {
                                        decryptedText = "Decryption error : Wrong password"
                                    }
                                    isDialogOpen = false

                                    AlertDialog.Builder(context)
                                        .setTitle("Decrypted text")
                                        .setMessage(decryptedText)
                                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                        .show()

                                }
                            },
                            content = { Text("Decrypt") }
                        )
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                isDialogOpen = false
                            },
                            content = { Text("Cancel") }
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Decrypt") },
                onClick = { isDialogOpen = true },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null
                    )
                })

            DropdownMenuItem(
                text = { Text(R.string.delete.string) },
                onClick = {
                    showDeleteDialog = true
                    menuExpanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                })
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete_this_memo.string) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            archivedMemoListViewModel.deleteMemo(memo.id).suspendOnSuccess {
                                showDeleteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(R.string.confirm.string)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }
}