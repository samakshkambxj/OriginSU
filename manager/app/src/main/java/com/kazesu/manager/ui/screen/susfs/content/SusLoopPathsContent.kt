package com.kazesu.manager.ui.screen.susfs.content

import androidx.compose.runtime.Composable
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode
import com.kazesu.manager.ui.screen.susfs.content.miuix.SusLoopPathsContentMiuix
import com.kazesu.manager.ui.screen.susfs.content.material.SusLoopPathsContentMaterial

@Composable
fun SusLoopPathsContent(
    susLoopPaths: Set<String>,
    isLoading: Boolean,
    onAddLoopPath: () -> Unit,
    onRemoveLoopPath: (String) -> Unit,
    onEditLoopPath: ((String) -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SusLoopPathsContentMiuix(
            susLoopPaths = susLoopPaths,
            isLoading = isLoading,
            onAddLoopPath = onAddLoopPath,
            onRemoveLoopPath = onRemoveLoopPath,
            onEditLoopPath = onEditLoopPath,
            onReset = onReset
        )
        UiMode.Material -> SusLoopPathsContentMaterial(
            susLoopPaths = susLoopPaths,
            isLoading = isLoading,
            onAddLoopPath = onAddLoopPath,
            onRemoveLoopPath = onRemoveLoopPath,
            onEditLoopPath = { onEditLoopPath?.invoke(it) },
            onReset = { onReset?.invoke() }
        )
    }
}
