package com.geeksville.mta.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mta.ModuleConfigProtos.ModuleConfig.RemoteHardwareConfig
import com.geeksville.mta.copy
import com.geeksville.mta.ui.components.EditListPreference
import com.geeksville.mta.ui.components.PreferenceCategory
import com.geeksville.mta.ui.components.PreferenceFooter
import com.geeksville.mta.ui.components.SwitchPreference

@Composable
fun RemoteHardwareConfigItemList(
    remoteHardwareConfig: RemoteHardwareConfig,
    enabled: Boolean,
    onSaveClicked: (RemoteHardwareConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var remoteHardwareInput by remember(remoteHardwareConfig) { mutableStateOf(remoteHardwareConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Remote Hardware Config") }

        item {
            SwitchPreference(title = "Remote Hardware enabled",
                checked = remoteHardwareInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    remoteHardwareInput = remoteHardwareInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Allow undefined pin access",
                checked = remoteHardwareInput.allowUndefinedPinAccess,
                enabled = enabled,
                onCheckedChange = {
                    remoteHardwareInput = remoteHardwareInput.copy { allowUndefinedPinAccess = it }
                })
        }
        item { Divider() }

        item {
            EditListPreference(title = "Available pins",
                list = remoteHardwareInput.availablePinsList,
                maxCount = 4, // available_pins max_count:4
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValuesChanged = { list ->
                    remoteHardwareInput = remoteHardwareInput.copy {
                        availablePins.clear()
                        availablePins.addAll(list)
                    }
                })
        }

        item {
            PreferenceFooter(
                enabled = remoteHardwareInput != remoteHardwareConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    remoteHardwareInput = remoteHardwareConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(remoteHardwareInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoteHardwareConfigPreview() {
    RemoteHardwareConfigItemList(
        remoteHardwareConfig = RemoteHardwareConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
