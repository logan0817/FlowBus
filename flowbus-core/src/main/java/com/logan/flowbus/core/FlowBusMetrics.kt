package com.logan.flowbus.core

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class FlowBusMetrics {
    private val acceptedPostCount = AtomicLong(0)
    private val rejectedPostCount = AtomicLong(0)
    private val lastPostTimeMillis = AtomicLong(0)
    private val lastRejectedReason = AtomicReference<FlowBusPostOutcome?>(null)

    fun record(result: FlowBusPostResult, nowMillis: Long = System.currentTimeMillis()) {
        if (result.accepted) {
            acceptedPostCount.incrementAndGet()
        } else {
            rejectedPostCount.incrementAndGet()
            lastRejectedReason.set(result.outcome)
        }
        lastPostTimeMillis.set(nowMillis)
    }

    fun snapshot(): FlowBusEventMetricsSnapshot {
        return FlowBusEventMetricsSnapshot(
            acceptedPostCount = acceptedPostCount.get(),
            rejectedPostCount = rejectedPostCount.get(),
            lastPostTimeMillis = lastPostTimeMillis.get().takeIf { it > 0 },
            lastRejectedReason = lastRejectedReason.get()
        )
    }
}

internal fun emptyFlowBusEventMetricsSnapshot(): FlowBusEventMetricsSnapshot {
    return FlowBusEventMetricsSnapshot(
        acceptedPostCount = 0,
        rejectedPostCount = 0,
        lastPostTimeMillis = null,
        lastRejectedReason = null
    )
}

internal fun combineFlowBusEventMetricsSnapshots(
    first: FlowBusEventMetricsSnapshot,
    second: FlowBusEventMetricsSnapshot
): FlowBusEventMetricsSnapshot {
    val firstTime = first.lastPostTimeMillis ?: 0
    val secondTime = second.lastPostTimeMillis ?: 0
    val latestSnapshot = if (secondTime >= firstTime) second else first
    return FlowBusEventMetricsSnapshot(
        acceptedPostCount = first.acceptedPostCount + second.acceptedPostCount,
        rejectedPostCount = first.rejectedPostCount + second.rejectedPostCount,
        lastPostTimeMillis = maxOf(firstTime, secondTime).takeIf { it > 0 },
        lastRejectedReason = latestSnapshot.lastRejectedReason ?: first.lastRejectedReason ?: second.lastRejectedReason
    )
}
