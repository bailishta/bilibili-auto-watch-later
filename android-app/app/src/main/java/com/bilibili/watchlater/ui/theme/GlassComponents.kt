package com.bilibili.watchlater.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
              shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
              content: @Composable RowScope.() -> Unit) =
    AppAction(onClick, modifier, enabled, shape, colors, null, 0, content)

@Composable
fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                      shape: Shape = ButtonDefaults.outlinedShape,
                      colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
                      border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
                      content: @Composable RowScope.() -> Unit) =
    AppAction(onClick, modifier, enabled, shape, colors, border, 1, content)

@Composable
fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  content: @Composable RowScope.() -> Unit) =
    AppAction(onClick, modifier, enabled, ButtonDefaults.textShape, ButtonDefaults.textButtonColors(), null, 2, content)

@Composable
private fun AppAction(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, shape: Shape,
                      colors: ButtonColors, border: BorderStroke?, variant: Int,
                      content: @Composable RowScope.() -> Unit) {
    when (variant) {
        1 -> OutlinedButton(onClick, modifier, enabled, shape, colors, border = border, content = content)
        2 -> TextButton(onClick, modifier, enabled, shape, colors, content = content)
        else -> Button(onClick, modifier, enabled, shape, colors, content = content)
    }
}

@Composable
fun AppIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  content: @Composable () -> Unit) {
    IconButton(onClick, modifier, enabled = enabled, content = content)
}

@Composable
fun AppOutlinedTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false, shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()) {
    OutlinedTextField(value, onValueChange, modifier, enabled, readOnly,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        suffix = suffix, isError = isError, keyboardOptions = keyboardOptions, singleLine = singleLine,
        shape = shape, colors = colors)
}

@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
                  modifier: Modifier = Modifier, enabled: Boolean = true,
                  colors: SelectableChipColors = FilterChipDefaults.filterChipColors()) {
    FilterChip(selected, onClick, label, modifier, enabled, colors = colors)
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(checked, onCheckedChange, enabled = enabled)
}

@Composable
fun AppAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
                   dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
                   text: (@Composable () -> Unit)? = null) {
    AlertDialog(onDismissRequest, confirmButton, dismissButton = dismissButton, title = title, text = text)
}

@Composable
fun AppSnackbar(data: SnackbarData) {
    Snackbar(data)
}
