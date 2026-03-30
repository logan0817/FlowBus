package com.logan.flowbus

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

internal suspend fun <T : Any> collectEventFlowSequentially(
    flow: Flow<Any>,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    flow.collect { value ->
        handleReceivedEventSequentially(value, dispatcher, onReceived)
    }
}

@Suppress("UNCHECKED_CAST")
internal suspend fun <T : Any> handleReceivedEventSequentially(
    value: Any,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    try {
        if (dispatcher == null) {
            onReceived(value as T)
        } else {
            withContext(dispatcher) {
                onReceived(value as T)
            }
        }
    } catch (e: ClassCastException) {
        Log.w("FlowEventBus", "handleReceivedEventSequentially ClassCastException:$e")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("FlowEventBus", "handleReceivedEventSequentially Exception:$e")
    }
}
