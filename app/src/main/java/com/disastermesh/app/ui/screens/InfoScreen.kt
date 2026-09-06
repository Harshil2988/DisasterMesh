package com.disastermesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.ReadoutRow
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.theme.Mesh

/**
 * The explainer. Written for someone who has never heard of a mesh network —
 * a judge, a volunteer, or a person in an actual emergency.
 */
@Composable
fun InfoScreen(
    messageNotifications: Boolean,
    sosNotifications: Boolean,
    onMessageNotificationsChange: (Boolean) -> Unit,
    onSosNotificationsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xl)) {

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
            Text(
                "DisasterMesh",
                style = MaterialTheme.typography.displaySmall,
                color = Mesh.Text.Primary
            )
            Text(
                "Communication that keeps working when the network does not.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mesh.Text.Secondary
            )
        }

        Section("Why it matters") {
            Text(
                "In a disaster, towers lose power and networks fail exactly when people " +
                    "most need to reach each other. DisasterMesh turns the phones " +
                    "themselves into the network, so a message can still travel.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mesh.Text.Secondary
            )
        }

        Section("How it works") {
            Step(1, "Your phone becomes a node", "It advertises itself and listens for others over Bluetooth and Wi-Fi. No SIM, no towers, no internet.")
            Step(2, "Nearby nodes link up", "Phones in range connect automatically. Nobody picks a role — every device does the same job.")
            Step(3, "Messages hop onward", "A node that receives a message passes it to its own neighbours.")
            Step(4, "Reach beyond your range", "Your message arrives at phones you could never reach directly.")
        }

        Section("The safeguards") {
            Explain("Every message is unique", "Each carries an ID, so a node that has already seen one ignores the copy instead of echoing it forever.")
            Explain("Messages expire", "A hop counter (TTL) drops by one at each relay. At zero the message stops, so nothing circulates indefinitely.")
            Explain("The sender is preserved", "Relaying never rewrites who sent a message or where they were.")
            Explain("SOS carries location", "An emergency attaches your coordinates at the moment you send it. Ordinary messages never request GPS.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            SectionLabel("Notifications")
            MeshPanel {
                Toggle(
                    "Message notifications",
                    "Alert me when a normal message arrives.",
                    messageNotifications,
                    onMessageNotificationsChange
                )
                Toggle(
                    "SOS notifications",
                    "Alert me when an emergency broadcast arrives.",
                    sosNotifications,
                    onSosNotificationsChange
                )
                Text(
                    "These only silence alerts. Messages are still received, shown here, " +
                        "and still relayed to other nodes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Tertiary
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            SectionLabel("Requirements")
            MeshPanel {
                ReadoutRow("Internet", "Not required", Mesh.Signal.Ok)
                ReadoutRow("Cloud server", "Not required", Mesh.Signal.Ok)
                ReadoutRow("Mobile signal", "Not required", Mesh.Signal.Ok)
                ReadoutRow("Mesh relay", "Enabled", Mesh.Signal.Live)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            SectionLabel("Honest limits")
            MeshPanel(background = Mesh.Surface.Sunken) {
                Limit("Range is short — each hop is Bluetooth/Wi-Fi distance, roughly tens of metres and less through walls.")
                Limit("Supports multiple nearby mesh nodes, subject to device and radio limitations. Expect a handful of simultaneous links per phone, not unlimited.")
                Limit("Messages stop after 5 hops.")
                Limit("There is no delivery receipt. A sent message is never claimed to have arrived.")
                Limit("Messages are not encrypted. Anyone nearby running this app can read them.")
                Limit("Every phone needs this app running, with Bluetooth, Wi-Fi and Location switched on.")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        SectionLabel(title)
        MeshPanel { content() }
    }
}

@Composable
private fun Step(number: Int, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.lg)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Mesh.Signal.LiveDeep.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Mesh.Signal.Live
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Text.Primary
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Tertiary)
        }
    }
}

@Composable
private fun Explain(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Mesh.Text.Primary
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Secondary)
    }
}

@Composable
private fun Toggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Mesh.TouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Text.Primary
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Tertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mesh.Text.OnAccent,
                checkedTrackColor = Mesh.Signal.Live,
                uncheckedThumbColor = Mesh.Text.Tertiary,
                uncheckedTrackColor = Mesh.Surface.Sunken,
                uncheckedBorderColor = Mesh.Line.Strong
            )
        )
    }
}

@Composable
private fun Limit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .background(Mesh.Text.Tertiary, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Secondary
        )
    }
}
