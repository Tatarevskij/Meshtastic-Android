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
import com.geeksville.mta.ModuleConfigProtos.ModuleConfig.RangeTestConfig
import com.geeksville.mta.copy
import com.geeksville.mta.ui.components.EditTextPreference
import com.geeksville.mta.ui.components.PreferenceCategory
import com.geeksville.mta.ui.components.PreferenceFooter
import com.geeksville.mta.ui.components.SwitchPreference

@Composable
fun RangeTestConfigItemList(
    rangeTestConfig: RangeTestConfig,
    enabled: Boolean,
    onSaveClicked: (RangeTestConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var rangeTestInput by remember(rangeTestConfig) { mutableStateOf(rangeTestConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Range Test Config") }

        item {
            SwitchPreference(title = "Range test enabled",
                checked = rangeTestInput.enabled,
                enabled = enabled,
                onCheckedChange = { rangeTestInput = rangeTestInput.copy { this.enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Sender message interval (seconds)",
                value = rangeTestInput.sender,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { rangeTestInput = rangeTestInput.copy { sender = it } })
        }

        item {
            SwitchPreference(title = "Save .CSV in storage (ESP32 only)",
                checked = rangeTestInput.save,
                enabled = enabled,
                onCheckedChange = { rangeTestInput = rangeTestInput.copy { save = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = rangeTestInput != rangeTestConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    rangeTestInput = rangeTestConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(rangeTestInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RangeTestConfig() {
    RangeTestConfigItemList(
        rangeTestConfig = RangeTestConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
