package com.velos.net.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.HorizontalDivider

@Composable
fun MiuixDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.dividerLine
    )
}

@Composable
fun SectionTag(text: String) {
    SmallTitle(text = text)
}

@Composable
fun FormRow(
    label: String,
    value: String,
    unit: String,
    placeholder: String = "默认为空不生效",
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.width(45.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
                textStyle = TextStyle(fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        androidx.compose.material3.Text(
                            placeholder, fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        )
                    }
                    inner()
                }
            )
        }
        androidx.compose.material3.Text(
            unit, fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
    MiuixDivider()
}

@Composable
fun FormRowHalf(
    label1: String, value1: String, unit1: String, onValue1Change: (String) -> Unit,
    label2: String, value2: String, unit2: String, onValue2Change: (String) -> Unit,
    placeholder1: String = "默认为空", placeholder2: String = "0-100"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                label1, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.width(40.dp)
            )
            BasicTextField(
                value = value1,
                onValueChange = { onValue1Change(it.filter { c -> c.isDigit() }) },
                textStyle = TextStyle(fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value1.isEmpty()) {
                        androidx.compose.material3.Text(placeholder1, fontSize = 14.sp, color = MiuixTheme.colorScheme.disabledOnSecondaryVariant)
                    }
                    inner()
                }
            )
            androidx.compose.material3.Text(unit1, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f).height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                label2, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.width(40.dp)
            )
            BasicTextField(
                value = value2,
                onValueChange = { onValue2Change(it.filter { c -> c.isDigit() }) },
                textStyle = TextStyle(fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value2.isEmpty()) {
                        androidx.compose.material3.Text(placeholder2, fontSize = 14.sp, color = MiuixTheme.colorScheme.disabledOnSecondaryVariant)
                    }
                    inner()
                }
            )
            androidx.compose.material3.Text(unit2, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    MiuixDivider()
}

@Composable
fun ProtocolSwitch(name: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(
            name, fontSize = 16.sp,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
