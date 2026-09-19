package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BenchmarkTaskBank {

    public List<BenchmarkTask> getTasks(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {

            return List.of();
        }

        return switch (
                domain.trim().toLowerCase()
        ) {

            case "knowledge" ->
                    knowledgeTasks();

            case "reasoning" ->
                    reasoningTasks();

            case "research" ->
                    researchTasks();

            case "coding" ->
                    codingTasks();

            case "memory" ->
                    memoryTasks();

            default ->
                    List.of();
        };
    }

    private List<BenchmarkTask> knowledgeTasks() {

        List<BenchmarkTask> tasks =
                new ArrayList<>();

        tasks.add(
                task(
                        "What is the capital of France?",
                        "paris",
                        1
                )
        );

        tasks.add(
                task(
                        "What planet is known as the Red Planet?",
                        "mars",
                        1
                )
        );

        tasks.add(
                task(
                        "What is the chemical formula for water?",
                        "h2o",
                        1
                )
        );

        tasks.add(
                task(
                        "What gas do humans need to breathe?",
                        "oxygen",
                        1
                )
        );

        tasks.add(
                task(
                        "What is the largest ocean on Earth?",
                        "pacific",
                        2
                )
        );

        tasks.add(
                task(
                        "What is the process by which plants "
                                + "convert light into chemical energy?",
                        "photosynthesis",
                        2
                )
        );

        tasks.add(
                task(
                        "What is the smallest prime number?",
                        "2",
                        1
                )
        );

        tasks.add(
                task(
                        "What does CPU stand for?",
                        "central processing unit",
                        2
                )
        );

        tasks.add(
                task(
                        "What does RAM stand for?",
                        "random access memory",
                        2
                )
        );

        tasks.add(
                task(
                        "What is the speed of light commonly "
                                + "approximated as in vacuum?",
                        "299792458",
                        3
                )
        );

        return tasks;
    }

    private List<BenchmarkTask> reasoningTasks() {

        List<BenchmarkTask> tasks =
                new ArrayList<>();

        tasks.add(
                task(
                        "What comes next: 2, 4, 6, 8?",
                        "10",
                        1
                )
        );

        tasks.add(
                task(
                        "What comes next: 3, 6, 12, 24?",
                        "48",
                        2
                )
        );

        tasks.add(
                task(
                        "If all cats are animals and Luna is a cat, "
                                + "is Luna an animal?",
                        "yes",
                        1
                )
        );

        tasks.add(
                task(
                        "If A is greater than B and B is greater "
                                + "than C, is A greater than C?",
                        "yes",
                        1
                )
        );

        tasks.add(
                task(
                        "A box has 3 red balls and 2 blue balls. "
                                + "How many balls are there?",
                        "5",
                        1
                )
        );

        tasks.add(
                task(
                        "If five machines make five products "
                                + "in five minutes, how many products "
                                + "does one machine make in five minutes?",
                        "1",
                        3
                )
        );

        tasks.add(
                task(
                        "If today is Monday, what day will it be "
                                + "three days from now?",
                        "thursday",
                        2
                )
        );

        tasks.add(
                task(
                        "A number is greater than 10 and less than 12. "
                                + "What integer is it?",
                        "11",
                        2
                )
        );

        tasks.add(
                task(
                        "What is the next number: 1, 1, 2, 3, 5, 8?",
                        "13",
                        2
                )
        );

        tasks.add(
                task(
                        "If no birds are mammals and a penguin is a bird, "
                                + "is the penguin a mammal?",
                        "no",
                        3
                )
        );

        return tasks;
    }

    private List<BenchmarkTask> researchTasks() {

        List<BenchmarkTask> tasks =
                new ArrayList<>();

        tasks.add(
                task(
                        "Find reliable information about "
                                + "the Java programming language.",
                        "java",
                        2
                )
        );

        tasks.add(
                task(
                        "Research what HTTP stands for.",
                        "hypertext transfer protocol",
                        1
                )
        );

        tasks.add(
                task(
                        "Research what an API is.",
                        "application programming interface",
                        2
                )
        );

        tasks.add(
                task(
                        "Research what Git is used for.",
                        "version control",
                        2
                )
        );

        tasks.add(
                task(
                        "Research what Linux is.",
                        "operating system",
                        2
                )
        );

        tasks.add(
                task(
                        "Research the difference between "
                                + "RAM and storage.",
                        "ram",
                        2
                )
        );

        tasks.add(
                task(
                        "Research what HTTPS provides compared "
                                + "with HTTP.",
                        "encryption",
                        3
                )
        );

        return tasks;
    }

    private List<BenchmarkTask> codingTasks() {

        List<BenchmarkTask> tasks =
                new ArrayList<>();

        tasks.add(
                task(
                        """
                        Write a Java method named add that
                        receives two integers and returns
                        their sum.
                        """,
                        "return",
                        1
                )
        );

        tasks.add(
                task(
                        "Which Java keyword is used to inherit "
                                + "from another class?",
                        "extends",
                        1
                )
        );

        tasks.add(
                task(
                        "Which data structure follows FIFO order?",
                        "queue",
                        1
                )
        );

        tasks.add(
                task(
                        "Which data structure commonly follows "
                                + "LIFO order?",
                        "stack",
                        1
                )
        );

        tasks.add(
                task(
                        "What keyword creates an object in Java?",
                        "new",
                        1
                )
        );

        tasks.add(
                task(
                        "What Java keyword prevents a class "
                                + "from being inherited?",
                        "final",
                        2
                )
        );

        tasks.add(
                task(
                        "What is the time complexity of binary search "
                                + "on a sorted array?",
                        "log",
                        3
                )
        );

        tasks.add(
                task(
                        "What design principle recommends that a class "
                                + "should have one reason to change?",
                        "single responsibility",
                        3
                )
        );

        return tasks;
    }

    private List<BenchmarkTask> memoryTasks() {

        List<BenchmarkTask> tasks =
                new ArrayList<>();

        tasks.add(
                task(
                        "What does persistent storage mean?",
                        "storage",
                        1
                )
        );

        tasks.add(
                task(
                        "Why is context useful to an AI assistant?",
                        "context",
                        1
                )
        );

        tasks.add(
                task(
                        "What is long-term memory used for "
                                + "in an AI assistant?",
                        "memory",
                        2
                )
        );

        tasks.add(
                task(
                        "Why should duplicated knowledge be avoided?",
                        "duplicate",
                        2
                )
        );

        tasks.add(
                task(
                        "Why should stored information have a source?",
                        "source",
                        2
                )
        );

        return tasks;
    }

    private BenchmarkTask task(
            String prompt,
            String expected,
            int difficulty
    ) {

        return new BenchmarkTask(
                prompt,
                List.of(expected),
                difficulty
        );
    }

    public record BenchmarkTask(
            String prompt,
            List<String> expected,
            int difficulty
    ) {
    }
}
