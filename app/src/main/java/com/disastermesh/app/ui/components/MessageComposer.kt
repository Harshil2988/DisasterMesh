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
    onStartRecording: () -> Unit,
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

            // Microphone sits beside send, never replacing it: text messaging is
            // unchanged whether or not audio is used.
            IconButton(
                onClick = onStartRecording,
                modifier = Modifier.size(Mesh.TouchTarget),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Mesh.Surface.Raised,
                    contentColor = Mesh.Signal.Live
                )
            ) {
                Icon(
                    imageVector = MicGlyph,
                    contentDescription = "Record voice message",
                    modifier = Modifier.size(20.dp)
                )
            }

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
/** A microphone drawn as a vector so it tints and scales with the design system. */
private val MicGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Capsule.
            moveTo(12f, 3f)
            curveTo(10.6f, 3f, 9.5f, 4.1f, 9.5f, 5.5f)
            lineTo(9.5f, 11.5f)
            curveTo(9.5f, 12.9f, 10.6f, 14f, 12f, 14f)
            curveTo(13.4f, 14f, 14.5f, 12.9f, 14.5f, 11.5f)
            lineTo(14.5f, 5.5f)
            curveTo(14.5f, 4.1f, 13.4f, 3f, 12f, 3f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            // Stand.
            moveTo(6.5f, 11.5f)
            lineTo(8f, 11.5f)
            curveTo(8f, 13.7f, 9.8f, 15.5f, 12f, 15.5f)
            curveTo(14.2f, 15.5f, 16f, 13.7f, 16f, 11.5f)
            lineTo(17.5f, 11.5f)
            curveTo(17.5f, 14.3f, 15.4f, 16.6f, 12.75f, 16.95f)
            lineTo(12.75f, 20f)
            lineTo(11.25f, 20f)
            lineTo(11.25f, 16.95f)
            curveTo(8.6f, 16.6f, 6.5f, 14.3f, 6.5f, 11.5f)
            close()
        }
    }.build()
}

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
