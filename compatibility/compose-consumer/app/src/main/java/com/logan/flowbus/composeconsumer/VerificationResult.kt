package com.logan.flowbus.composeconsumer

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 测试结果放在 Compose 层级之外，避免新版 Compose 过滤仅有 UIAutomator 的查询。 */
@Composable
internal fun VerificationResult(text: String) {
    val context = LocalContext.current
    val view = remember(context) { TextView(context) }
    DisposableEffect(context, view) {
        (context as Activity).addContentView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        onDispose { (view.parent as? ViewGroup)?.removeView(view) }
    }
    SideEffect { view.text = text }
}
