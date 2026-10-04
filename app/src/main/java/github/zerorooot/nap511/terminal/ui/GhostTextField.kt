package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GhostTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    ghostText: String,
    prompt: String,
    isWaitingConfirmation: Boolean,
    onSubmit: () -> Unit,
    onTabOrRight: () -> Unit,
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val textStyle = TextStyle(
        color = Color(0xFFECEFF1),
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 20.sp
    )

    val promptColor = if (isWaitingConfirmation) Color(0xFFFFD54F) else Color(0xFF69F0AE)
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitAndKeepKeyboard = {
        onSubmit()
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 终端提示符
        Text(
            text = prompt,
            color = promptColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            // 输入框
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(Color(0xFF69F0AE)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = { submitAndKeepKeyboard() },
                    onGo = { submitAndKeepKeyboard() },
                    onDone = { submitAndKeepKeyboard() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        when (event.key) {
                            Key.Tab -> {
                                onTabOrRight()
                                true
                            }
                            Key.MoveEnd -> {
                                if (value.selection.end == value.text.length && ghostText.isNotEmpty()) {
                                    onTabOrRight()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionRight -> {
                                if (value.selection.end == value.text.length && ghostText.isNotEmpty()) {
                                    onTabOrRight()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionUp -> {
                                onArrowUp()
                                true
                            }
                            Key.DirectionDown -> {
                                onArrowDown()
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
            )

            // 行内幽灵文本层 (Inline Ghost Text)
            // 仅当光标位于末尾且存在建议时渲染在末尾
            if (ghostText.isNotEmpty() && value.selection.end == value.text.length) {
                Text(
                    text = buildAnnotatedString {
                        // 前缀使用透明色占位，保证宽度与用户已输入文本完全一致
                        withStyle(style = SpanStyle(color = Color.Transparent)) {
                            append(value.text)
                        }
                        // 后缀以浅灰淡色展示幽灵文本
                        withStyle(style = SpanStyle(color = Color(0xFF888888), fontWeight = FontWeight.Normal)) {
                            append(ghostText)
                        }
                    },
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
