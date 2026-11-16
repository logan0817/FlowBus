package com.logan.flowbus.benchmark;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/** 仅在当前候选上比较诊断状态，不在历史正式 JAR 上运行新增 API。 */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class DiagnosticsBenchmark {
    @Param({"bus"}) public String adapter;
    @Param({"noSubscribers", "stickySend", "active1"}) public String scenario;
    @Param({"false", "true"}) public boolean enabled;
    private DiagnosticsWorkload workload;

    @Setup public void setup() {
        if (!"bus".equals(adapter)) throw new IllegalArgumentException("Candidate bus required");
        workload = new DiagnosticsWorkload(scenario, enabled);
    }

    @TearDown public void tearDown() { workload.close(); }

    @Benchmark public long batch() { return workload.run().getDigest(); }
}
