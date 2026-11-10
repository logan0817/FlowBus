package com.logan.flowbus.benchmark;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/** 仅供候选制品运行；旧版对照筛选不包含这个类。 */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SubscriptionBenchmark {
    @Param({"bus"}) public String adapter;
    @Param({"plain", "hook"}) public String scenario;
    private SubscriptionWorkload workload;

    @Setup public void setup() {
        if (!"bus".equals(adapter)) throw new IllegalArgumentException("Subscription benchmark requires candidate bus");
        workload = new SubscriptionWorkload(scenario);
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counts { public long sent; public long received; }

    @Benchmark public long operation(Counts counts) {
        Workload.Result result = workload.run();
        counts.sent += result.getSent();
        counts.received += result.getReceived();
        return result.getDigest();
    }
}
