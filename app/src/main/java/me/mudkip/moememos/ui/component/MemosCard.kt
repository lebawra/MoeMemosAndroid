package me.mudkip.moememos.ui.component

import android.app.AlertDialog
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemosRowStatus
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

@Composable
fun MemosCard(
    memo: Memo,
    previewMode: Boolean = false
) {
    val memosViewModel = LocalMemos.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .padding(horizontal = 15.dp, vertical = 10.dp)
            .fillMaxWidth(),
        border = if (memo.pinned && memo.rowStatus == MemosRowStatus.NORMAL) { BorderStroke(1.dp, MaterialTheme.colorScheme.primary) } else { null }
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 15.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(DateUtils.getRelativeTimeSpanString(memo.createdTs * 1000, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                if (LocalUserState.current.currentUser?.memoVisibility != memo.visibility) {
                    Icon(
                        memo.visibility.icon,
                        contentDescription = stringResource(memo.visibility.titleResource),
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                MemosCardActionButton(memo)
            }

            MemoContent(memo, previewMode = previewMode, checkboxChange = { checked, startOffset, endOffset ->
                scope.launch {
                    var text = memo.content.substring(startOffset, endOffset)
                    text = if (checked) {
                        text.replace("[ ]", "[x]")
                    } else {
                        text.replace("[x]", "[ ]")
                    }
                    memosViewModel.editMemo(memo.id, memo.content.replaceRange(startOffset, endOffset, text), memo.resourceList)
                }
            })
        }
    }
}

@Composable
fun MemosCardActionButton(
    memo: Memo
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val memosViewModel = LocalMemos.current
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier
        .fillMaxSize()
        .wrapContentSize(Alignment.TopEnd)) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (memo.pinned) {
                DropdownMenuItem(
                    text = { Text(R.string.unpin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.id, false).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PinDrop,
                            contentDescription = null
                        )
                    })
            } else {
                DropdownMenuItem(
                    text = { Text(R.string.pin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.id, true).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                text = { Text(R.string.edit.string) },
                onClick = {
                    rootNavController.navigate("${RouteName.EDIT}?memoId=${memo.id}")
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.share.string) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, memo.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Share,
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
                text = { Text(R.string.archive.string) },
                onClick = {
                    scope.launch {
                        memosViewModel.archiveMemo(memo.id).suspendOnSuccess {
                            menuExpanded = false
                        }
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null
                    )
                })
        }
    }
}