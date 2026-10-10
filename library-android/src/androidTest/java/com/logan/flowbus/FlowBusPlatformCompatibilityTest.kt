package com.logan.flowbus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** 同一用例应在 API 21 与较新系统执行，消费者不需要 core library desugaring。 */
@RunWith(AndroidJUnit4::class)
class FlowBusPlatformCompatibilityTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun normalStickyAndReopenedScopeWorkOnPlatform() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<Int>("platform.compatibility")
        val normal = async(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).first() }
        bus.post(key, 1)
        assertEquals(1, withTimeout(1_000) { normal.await() })
        bus.postSticky(key, 2)
        assertEquals(2, bus.stickyFlow(key).first())
        assertEquals(2, bus.consumeStickyLatest(key))

        val scope = bus.openScope("platform.scope")
        scope.postSticky(key, 3)
        assertEquals(3, scope.stickyFlow(key).first())
        assertTrue(scope.closeSuspending().closed)
        val reopened = bus.openScope(scope.scopeName)
        try {
            reopened.postSticky(key, 4)
            assertEquals(4, reopened.stickyFlow(key).first())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun explicitSuspendingSubscriptionCancelsAndRestartsWithoutClosingSource() = runBlocking {
        val bus = FlowBus()
        val source = bus.openScope("platform.lifecycle")
        val other = FlowBus()
        val key = eventKey<Int>("platform.lifecycle.event")
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val laterValues = Channel<Int>(Channel.UNLIMITED)
        source.postSticky(key, 1)
        other.postSticky(key, 99)
        val owner = withContext(Dispatchers.Main.immediate) {
            Owner().also { it.registry.currentState = Lifecycle.State.CREATED }
        }
        val subscription = withContext(Dispatchers.Main.immediate) {
            owner.collectEventFromSuspending(source, key, isSticky = true) { value ->
                if (value == 1) {
                    firstStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled.complete(Unit)
                    }
                } else {
                    laterValues.send(value)
                }
            }
        }
        try {
            withTimeout(5_000) {
                withContext(Dispatchers.Main.immediate) {
                    owner.registry.currentState = Lifecycle.State.STARTED
                }
                firstStarted.await()
                source.postSticky(key, 2)
                // 首条回调还在挂起，第二条不能被另一个协程并发处理。
                assertTrue(laterValues.tryReceive().isFailure)
                withContext(Dispatchers.Main.immediate) {
                    owner.registry.currentState = Lifecycle.State.CREATED
                }
                firstCancelled.await()
                source.postSticky(key, 3)
                other.postSticky(key, 100)
                withContext(Dispatchers.Main.immediate) {
                    owner.registry.currentState = Lifecycle.State.STARTED
                }
                assertEquals(3, laterValues.receive())
                withContext(Dispatchers.Main.immediate) {
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
                subscription.join()
                assertFalse(source.isClosed)
                assertTrue(source.postSticky(key, 4))
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
            subscription.cancelAndJoin()
            source.close()
            laterValues.close()
        }
    }
}
