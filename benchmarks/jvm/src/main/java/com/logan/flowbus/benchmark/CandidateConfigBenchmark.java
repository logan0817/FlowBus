package com.logan.flowbus.benchmark;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CandidateConfigBenchmark {
    @Param({"bus"}) public String adapter;
    private Workload workload;
    @Setup public void setup() { workload = new Workload(adapter, "configuredRegistration"); }
    @Benchmark public long operation() { return workload.run().getDigest(); }
}
