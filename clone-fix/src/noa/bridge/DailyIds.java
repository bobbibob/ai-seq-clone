package noa.bridge;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Independent of clone databases: deletion must never erase this journal. */
public final class DailyIds {
    public static final int MAX_ID = 254;
    private final File journal;

    public DailyIds(File journal) { this.journal = journal; }

    private static String checksum(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }

    /** Reserve before attempting creation; failed attempts consume their ID too. */
    public int reserve(String day, int bootstrapHighWater, Set<Integer> occupied) throws Exception {
        synchronized (DailyIds.class) {
            return reserveLocked(day, bootstrapHighWater, occupied);
        }
    }

    private int reserveLocked(String day, int bootstrapHighWater, Set<Integer> occupied)
            throws Exception {
        LocalDate.parse(day);
        File parent = journal.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create ID directory");
        try (RandomAccessFile lockFile = new RandomAccessFile(new File(parent, "ids.lock"), "rw");
             FileChannel channel = lockFile.getChannel();
             FileLock lock = channel.lock()) {
            Map<String, Integer> used = new HashMap<>();
            boolean exists = journal.exists();
            if (exists) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(journal), StandardCharsets.UTF_8))) {
                    String row;
                    while ((row = reader.readLine()) != null) {
                        String[] fields = row.split(" ", -1);
                        if (fields.length != 3) throw new IOException("Damaged ID journal; refusing to reset");
                        LocalDate.parse(fields[0]);
                        int id = Integer.parseInt(fields[1]);
                        String payload = fields[0] + " " + fields[1];
                        if (id < 0 || id > MAX_ID || !checksum(payload).equals(fields[2])
                                || id <= used.getOrDefault(fields[0], -1))
                            throw new IOException("Invalid ID journal; refusing to reuse IDs");
                        used.put(fields[0], id);
                    }
                }
                if (used.isEmpty()) throw new IOException("Empty ID journal; explicit recovery required");
            }
            // On first migration seed from BOTH live clones and historical root registry.
            // On a new date start a fresh daily sequence. Retain previous dates for clock rollback.
            // Virtual user 0 is the base install; daily clone numbering starts at 1.
            int last = used.getOrDefault(day, exists ? 0 : Math.max(0, bootstrapHighWater));
            int next = last + 1;
            while (occupied.contains(next)) next++;
            if (next > MAX_ID) throw new IOException("Daily internal ID space exhausted (0..254); no reuse");
            String payload = day + " " + next;
            byte[] row = (payload + " " + checksum(payload) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(journal, true)) {
                output.write(row);
                output.getFD().sync();
            }
            return next;
        }
    }
}
