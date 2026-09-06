package com.disastermesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.components.MeshCard
import com.disastermesh.app.ui.components.ReadoutRow
import com.disastermesh.app.ui.components.SectionHeader
import com.disastermesh.app.ui.theme.MeshColors

/** Explains what DisasterMesh does, for anyone picking up the phone cold. */
@Composable
fun InfoScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            "DISASTERMESH",
            style = MaterialTheme.typography.headlineMedium,
            color = MeshColors.TextPrimary
        )
        Text(
            "Offline emergency communication using nearby smartphones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeshColors.TextSecondary
        )

        SectionHeader("How it works")
        MeshCard {
            Step("1", "No internet", "Towers are down. Phones cannot reach any network.")
            Connector()
            Step("2", "Nearby phones connect", "Each phone advertises and scans over Bluetooth and Wi-Fi, and links up automatically.")
            Connector()
            Step("3", "Messages hop between nodes", "A phone that receives a message passes it on to its own neighbours.")
            Connector()
            Step("4", "Information reaches distant nodes", "A message arrives at phones the sender cannot reach directly.")
        }

        SectionHeader("Status")
        MeshCard {
            ReadoutRow("Internet", "NOT REQUIRED", MeshColors.Green)
            ReadoutRow("Cloud server", "NOT REQUIRED", MeshColors.Green)
            ReadoutRow("Mesh relay", "ENABLED", MeshColors.Cyan)
        }

        SectionHeader("Honest limits")
        MeshCard {
            Limit("Range is short. Each hop is Bluetooth/Wi-Fi range — roughly tens of metres, less through walls.")
            Limit("A phone holds a limited number of simultaneous connections. The platform decides how many; it is a handful, not unlimited.")
            Limit("Messages stop after 5 hops.")
            Limit("Every phone needs this app running, with Bluetooth, Wi-Fi and Location switched on.")
            Limit("Messages are not encrypted. Anyone nearby running this app can read them.")
        }
    }
}

@Composable
private fun Step(number: String, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MeshColors.SurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MeshColors.Cyan
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MeshColors.TextPrimary
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }
    }
}

@Composable
private fun Connector() {
    Box(
        modifier = Modifier
            .padding(start = 13.dp)
            .size(width = 2.dp, height = 14.dp)
            .background(MeshColors.Border)
    )
}

@Composable
private fun Limit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("·", color = MeshColors.TextDim)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MeshColors.TextSecondary
        )
    }
}
