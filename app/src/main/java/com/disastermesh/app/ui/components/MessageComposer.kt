package com.disastermesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.ui.MeshViewModel
import com.disastermesh.app.ui.theme.Mesh

/**
 * The text composer, docked above the navigation bar on the Messages tab.
 *
 * Purely an input surface: it hands a string to the caller, which passes it to
 * the existing mesh send path.
 */
@Composable
fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val canSend = text.isNotBlank() && connected

    val sendContainer by animateColorAsState(
        targetValue = if (canSend) Mesh.Signal.Live else Mesh.Surface.Raised,
        animationSpec = tween(220),
        label = "sendBg"
    )

    Column(modifier = modifier.background(Mesh.Surface.Card)) {
        HorizontalDivider(color = Mesh.Line.Subtle)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    // Cap the length so a paste cannot exceed one payload.
                    if (new.length <= MeshViewModel.MAX_MESSAGE_LENGTH) onTextChange(new)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Message nearby nodes…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mesh.Text.Tertiary
                    )
                },
                textStyle = TextStyle(fontSize = 15.sp, color = Mesh.Text.Primary),
                shape = RoundedCornerShape(Mesh.Radius.xl),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mesh.Signal.Live,
                    unfocusedBorderColor = Mesh.Line.Subtle,
                    focusedContainerColor = Mesh.Surface.Sunken,
                    unfocusedContainerColor = Mesh.Surface.Sunken,
                    cursorColor = Mesh.Signal.Live
                )
            )

            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(Mesh.TouchTarget),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = sendContainer,
                    contentColor = Mesh.Text.OnAccent,
                    disabledContainerColor = Mesh.Surface.Raised,
                    disabledContentColor = Mesh.Text.Tertiary
                )
            ) {
                Icon(
                    imageVector = SendArrow,
                    contentDescription = "Send message",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Honest about why sending is unavailable, and about the length budget.
        val hint = when {
            !connected -> "No connected nodes — a message cannot leave this phone yet."
            text.length > MeshViewModel.MAX_MESSAGE_LENGTH - 40 ->
                "${MeshViewModel.MAX_MESSAGE_LENGTH - text.length} characters left"
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary,
                modifier = Modifier.padding(start = Mesh.Space.xl, bottom = Mesh.Space.sm)
            )
        }
    }
}

/**
 * A send arrow drawn as a vector rather than an emoji, so it inherits tint and
 * scales cleanly. Kept local — it is the only custom glyph the app needs.
 */
private val SendArrow: ImageVector by lazy {
    ImageVector.Builder(
        name = "SendArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(3.4f, 20.4f)
            lineTo(21f, 12f)
            lineTo(3.4f, 3.6f)
            lineTo(3.4f, 10.1f)
            lineTo(15f, 12f)
            lineTo(3.4f, 13.9f)
            close()
        }
    }.build()
}
