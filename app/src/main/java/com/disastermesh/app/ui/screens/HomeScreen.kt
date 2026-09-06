package com.disastermesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.MessageCategory
import com.disastermesh.app.ui.components.MeshCard
import com.disastermesh.app.ui.components.PulsingDot
import com.disastermesh.app.ui.components.ReadoutRow
import com.disastermesh.app.ui.components.SectionHeader
import com.disastermesh.app.ui.components.CategorySelector
import com.disastermesh.app.ui.components.SosButton
import com.disastermesh.app.ui.theme.MeshColors

/**
 * The dashboard. Every value shown here comes from the real [MeshState]
 * produced by NearbyConnectionManager — nothing is hardcoded.
 */
@Composable
fun HomeScreen(
    state: MeshState,
    permissionsGranted: Boolean,
    selectedCategory: MessageCategory,
    onSelectCategory: (MessageCategory) -> Unit,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSendHello: () -> Unit,
    onSendSos: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        // --- Identity header ---------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "DisasterMesh",
                style = MaterialTheme.typography.headlineMedium,
                color = MeshColors.TextPrimary
            )
            Text(
                state.nodeId.ifBlank { "NODE — ····" },
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MeshColors.Cyan
            )
            Text(
                "Every phone is a mesh node",
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }

        // --- Network status ----------------------------------------------
        val accent = if (state.meshActive) MeshColors.Cyan else MeshColors.Border
        MeshCard(accent = accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(
                    active = state.meshActive,
                    color = if (state.meshActive) MeshColors.Green else MeshColors.TextDim
                )
                Text(
                    if (state.meshActive) "MESH ACTIVE" else "MESH INACTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.meshActive) MeshColors.Cyan else MeshColors.TextDim
                )
            }

            ReadoutRow(
                "Advertising",
                if (state.advertising) "YES" else "NO",
                if (state.advertising) MeshColors.Green else MeshColors.TextDim
            )
            ReadoutRow(
                "Discovering",
                if (state.discovering) "YES" else "NO",
                if (state.discovering) MeshColors.Green else MeshColors.TextDim
            )
            ReadoutRow(
                "Connected nodes",
                state.connectedCount.toString(),
                if (state.isConnected) MeshColors.Cyan else MeshColors.TextDim
            )
            ReadoutRow("Internet", "NOT REQUIRED", MeshColors.TextSecondary)

            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }

        // --- The single networking control --------------------------------
        if (!state.meshActive) {
            Button(
                onClick = onStartMesh,
                enabled = permissionsGranted,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshColors.Cyan,
                    contentColor = MeshColors.Background,
                    disabledContainerColor = MeshColors.SurfaceHigh,
                    disabledContentColor = MeshColors.TextDim
                )
            ) {
                Text("START MESH NODE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else {
            OutlinedButton(
                onClick = onStopMesh,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "STOP MESH NODE",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MeshColors.TextSecondary
                )
            }
        }

        // --- Emergency category + SOS -------------------------------------
        SectionHeader("Emergency category")
        CategorySelector(selected = selectedCategory, onSelect = onSelectCategory)

        SosButton(
            enabled = state.isConnected,
            categoryLabel = selectedCategory.label,
            onConfirmed = onSendSos,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        // --- Plain test message -------------------------------------------
        AnimatedVisibility(
            visible = state.isConnected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedButton(
                onClick = onSendHello,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("SEND HELLO", color = MeshColors.Cyan, fontWeight = FontWeight.SemiBold)
            }
        }

        // --- Connected peers summary --------------------------------------
        if (state.meshActive) {
            SectionHeader("Connected nodes (${state.connectedCount})")
            if (state.connectedPeers.isEmpty()) {
                Text(
                    "None yet. Nearby phones connect by themselves — there is nothing to tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshColors.TextDim
                )
            } else {
                state.connectedPeers.forEach { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeshColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, MeshColors.Border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(active = true, color = MeshColors.Green, size = 9)
                        Text(
                            peer.nodeId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MeshColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
