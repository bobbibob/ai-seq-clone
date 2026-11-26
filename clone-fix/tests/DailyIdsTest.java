import noa.bridge.DailyIds;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class DailyIdsTest {
    private static void expect(int wanted, int actual) {
        if (wanted != actual) throw new AssertionError("wanted " + wanted + ", got " + actual);
    }
    private static final Set<Integer> EMPTY = Collections.emptySet();
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("reserve")) {
            System.out.println(new DailyIds(new File(args[1])).reserve(args[2], -1, EMPTY));
            return;
        }
        File directory = Files.createTempDirectory("noa-id-tests-").toFile();
        File journal = new File(directory, "daily.ids");
        DailyIds ids = new DailyIds(journal);
        expect(24, ids.reserve("2026-11-26", 23, EMPTY));
        // All clones removed; new process/service instance still allocates 25.
        expect(25, new DailyIds(journal).reserve("2026-11-26", -1, EMPTY));
        // Failed creation already consumed 25; retry must use 26.
        expect(26, ids.reserve("2026-11-26", -1, EMPTY));
        expect(1, ids.reserve("2026-11-27", -1, EMPTY));
        expect(2, ids.reserve("2026-11-27", -1, EMPTY));
        expect(27, ids.reserve("2026-11-26", -1, EMPTY)); // clock rollback
        expect(4, ids.reserve("2026-11-27", -1, new HashSet<>(Arrays.asList(3))));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Set<Integer> results = new HashSet<>();
        List<Future<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 32; i++) tasks.add(pool.submit(
            () -> new DailyIds(journal).reserve("2026-11-27", -1, EMPTY)));
        for (Future<Integer> future : tasks) results.add(future.get());
        pool.shutdown();
        if (results.size() != 32) throw new AssertionError("duplicate concurrent IDs");
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < 8; i++) processes.add(new ProcessBuilder(
            System.getProperty("java.home") + "/bin/java", "-cp", System.getProperty("java.class.path"),
            "DailyIdsTest", "reserve", journal.toString(), "2026-11-27").redirectErrorStream(true).start());
        for (Process process : processes) {
            String output = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
            if (process.waitFor() != 0) throw new AssertionError("child failed: " + output);
            if (!results.add(Integer.parseInt(output))) throw new AssertionError("duplicate cross-process ID");
        }
        File exhausted = new File(directory, "exhausted.ids");
        expect(254, new DailyIds(exhausted).reserve("2026-11-26", 253, EMPTY));
        try {
            new DailyIds(exhausted).reserve("2026-11-26", -1, EMPTY);
            throw new AssertionError("ID space silently recycled");
        } catch (IOException expected) { }
        Files.write(journal.toPath(), "torn-write".getBytes(), StandardOpenOption.APPEND);
        try {
            ids.reserve("2026-11-27", -1, EMPTY);
            throw new AssertionError("damaged state silently reset");
        } catch (IOException expected) { }
        System.out.println("PASS: migration, deletion/restart, failed attempts, midnight, clock rollback, occupied IDs, threads, processes, exhaustion, corruption");
        System.out.println("Test files retained at " + directory);
    }
}
