package controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.BenchmarkEngine;

@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin(origins = "*")
public class BenchmarkController {

    private final BenchmarkEngine benchmarkEngine;

    public BenchmarkController(
            BenchmarkEngine benchmarkEngine
    ) {
        this.benchmarkEngine =
                benchmarkEngine;
    }

    @GetMapping("/{domain}")
    public BenchmarkEngine.BenchmarkResult run(
            @PathVariable String domain
    ) {

        return benchmarkEngine.run(
                domain
        );
    }

    @GetMapping("/all")
    public BenchmarkEngine.BenchmarkSummary all() {

        return benchmarkEngine.runAll();
    }
}
