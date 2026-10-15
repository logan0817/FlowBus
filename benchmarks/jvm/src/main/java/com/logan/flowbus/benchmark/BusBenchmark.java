package com.logan.flowbus.benchmark;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BusBenchmark {
    @Param({"bus", "shared"}) public String adapter;
    @Param({"noSubscribers", "active1", "active8", "slowSuspend", "slowDrop", "stickySend", "stickyReplay", "producers"}) public String scenario;
    private Workload workload;
    @Setup public void setup() { workload = new Workload(adapter, scenario); }
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counts { public long sent; public long received; }
    @Benchmark public long batch(Counts counts) {
        Workload.Result result = workload.run();
        counts.sent += result.getSent();
        counts.received += result.getReceived();
        return result.getDigest();
    }
}
