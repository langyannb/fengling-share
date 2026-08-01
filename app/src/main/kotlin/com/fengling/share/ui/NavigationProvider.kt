package com.fengling.share.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * 为 Miuix SearchBar 等组件提供 NavigationEventDispatcherOwner
 * (miuix 0.9.3 的 SearchBar 内部无条件调用 NavigationBackHandler, 需要此提供者)
 */
@Composable
fun ProvideNavigationEventDispatcher(content: @Composable () -> Unit) {
    val dispatcher = remember { NavigationEventDispatcher() }
    DisposableEffect(dispatcher) {
        onDispose { dispatcher.dispose() }
    }
    val owner = remember(dispatcher) {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher
                get() = dispatcher
        }
    }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides owner,
        content = content,
    )
}
