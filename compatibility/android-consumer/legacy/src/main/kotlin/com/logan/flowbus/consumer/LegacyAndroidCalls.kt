package com.logan.flowbus.consumer

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.collectEventFromSuspending
import com.logan.flowbus.onEventSuspending
import com.logan.flowbus.postStickyEventTo
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield

/** 仅用固定 1.1.0 core/AAR 编译；候选 APK 复用这些 class，禁止引用 1.2.0 API。 */
object LegacyAndroidCalls {
    private class Owner : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
    }

    suspend fun verify() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val bus = FlowBus()
        val shared = bus.scoped("legacy.shared")
        val owned = bus.openScope("legacy.owned")
        val key = eventKey<Int>("consumer.legacy.110")
        val rootDone = CompletableDeferred<Int>()
        val sharedDone = CompletableDeferred<Int>()
        val ownedDone = CompletableDeferred<Int>()
        val ownerDone = CompletableDeferred<Int>()
        val jobs = listOf(
            owner.collectEventFromSuspending(bus, key, isSticky = true) { yield(); rootDone.complete(it) },
            owner.collectEventFromSuspending(shared, key, isSticky = true) { yield(); sharedDone.complete(it) },
            owner.collectEventFromSuspending(owned, key, isSticky = true) { yield(); ownedDone.complete(it) },
            owner.onEventSuspending<Int>(from = owner, isSticky = true, eventName = key.name) { yield(); ownerDone.complete(it) }
        )
        try {
            bus.postSticky(key, 61)
            shared.postSticky(key, 62)
            owned.postSticky(key, 63)
            postStickyEventTo(owner, 64, eventName = key.name)
            check(rootDone.await() == 61)
            check(sharedDone.await() == 62)
            check(ownedDone.await() == 63)
            check(ownerDone.await() == 64)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            jobs.forEach { it.cancel() }
            owned.close()
            shared.removeScope()
            owner.viewModelStore.clear()
            jobs.forEach { it.join() }
        }
    }
}
