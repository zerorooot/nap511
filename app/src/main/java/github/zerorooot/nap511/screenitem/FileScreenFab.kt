package github.zerorooot.nap511.screenitem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FileScreenFab(
    isCutState: Boolean,
    visible: Boolean,
    onCancelCut: () -> Unit,
    onCutPaste: () -> Unit,
    onAddFolder: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + scaleIn() + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + scaleOut() + fadeOut()
    ) {
        AnimatedContent(
            targetState = isCutState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "FabAnimation"
        ) { isCut ->
            if (isCut) {
                Column {
                    FloatingActionButton(onClick = onCancelCut) {
                        Icon(Icons.Filled.Close, "close")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(onClick = onCutPaste) {
                        Icon(Icons.Default.ContentPaste, "cut")
                    }
                }
            } else {
                FloatingActionButton(onClick = onAddFolder) {
                    Icon(Icons.Filled.Add, "add")
                }
            }
        }
    }
}
