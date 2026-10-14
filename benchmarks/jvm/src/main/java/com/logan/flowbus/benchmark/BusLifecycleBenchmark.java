package com.logan.flowbus.benchmark;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BusLifecycleBenchmark {
    @Param({"bus"}) public String adapter;
    @Param({"registration", "scope"}) public String scenario;
    private Workload workload;
    @Setup public void setup() { workload = new Workload(adapter, scenario); }
    @Benchmark public long operation() { return workload.run().getDigest(); }
}
