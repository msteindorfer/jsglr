package org.spoofax.jsglr2.benchmark.jsglr1;

import org.openjdk.jmh.annotations.Param;
import org.spoofax.jsglr2.testset.TestSet;

public class JSGLR1CSVBenchmark extends JSGLR1Benchmark {
    
    public JSGLR1CSVBenchmark() {
        super(TestSet.csv);
    }
    
    @Param({"1000", "2000", "4000"})
    public int n;

}
