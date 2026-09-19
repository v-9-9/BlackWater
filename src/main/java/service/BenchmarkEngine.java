package controller;

import org.springframework.web.bind.annotation.*;
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
    public BenchmarkSummary all() {

        BenchmarkEngine.BenchmarkResult knowledge =
                benchmarkEngine.run(
                        "knowledge"
                );

        BenchmarkEngine.BenchmarkResult reasoning =
                benchmarkEngine.run(
                        "reasoning"
                );

        BenchmarkEngine.BenchmarkResult research =
                benchmarkEngine.run(
                        "research"
                );

        BenchmarkEngine.BenchmarkResult coding =
                benchmarkEngine.run(
                        "coding"
                );

        BenchmarkEngine.BenchmarkResult memory =
                benchmarkEngine.run(
                        "memory"
                );

        int total =
                knowledge.score()
                        + reasoning.score()
                        + research.score()
                        + coding.score()
                        + memory.score();

        int average =
                total / 5;

        return new BenchmarkSummary(
                average,
                knowledge,
                reasoning,
                research,
                coding,
                memory
        );
    }

    public record BenchmarkSummary(
            int averageScore,
            BenchmarkEngine.BenchmarkResult knowledge,
            BenchmarkEngine.BenchmarkResult reasoning,
            BenchmarkEngine.BenchmarkResult research,
            BenchmarkEngine.BenchmarkResult coding,
            BenchmarkEngine.BenchmarkResult memory
    ) {
    }
}
