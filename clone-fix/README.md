# Persistent internal clone IDs — repair candidate

## Device state

The existing `ai-seq-clone` rotation entrypoint is paused and its Magisk startup
is disabled. No clones or app data were deleted. The original script and config
are retained on S20 at `/data/adb/aiseq/recovery-before-fix/`.

Restore the **old behavior**, not this candidate, from Termux with:

```sh
su -c 'sh /data/adb/aiseq/recovery-before-fix/restore.sh'
```

Restoring also restores the old ID-reset bug. A local recovery archive is at
`recovery/noa-before-id-fix.tgz`; it contains the installed bridge binary and
the original Termux launch scripts, not the original v0.3 bridge source.

## Candidate implementation

* `src/noa/bridge/DailyIds.java`: persistent, fsynced, checksummed reservation
  journal in Super Clone's **own** `getNoBackupFilesDir()`, outside virtual clone
  storage. Both JVM threads and separate processes are serialized. The state is
  not tied to clone names or the current clone list.
* `src/noa/bridge/Bridge.java`: adapter checked against the authenticated v0.3 source,
  modified to reserve an actual internal ID before deleting all target clones.
  including verified single-clone deletion and virtual-package consistency checks.
  Runtime adapter compatibility must still be validated before use.
* `rotate.py`: separate controller for video quota 10 and edit quota 2. Defaults
  to read-only. Execution checks the bridge capability, reads the exhausted
  quota twice from the same generator process, reserves/deletes/creates through
  the candidate bridge, verifies the clone list, then calls the existing launch
  and readiness scripts. Both quotas are monitored separately; unavailable
  counters are reported rather than treated as zero. A pending launch is retried without creating another
  clone. `--watch --execute` is implemented but has **not** been enabled.

First migration seeds from live IDs and the historical registry. With the
observed high-water mark of 23, the next internal ID is 24. Later clone deletion
cannot reset the journal. Failed creation attempts consume reservations, so
some IDs can be skipped rather than reused.

The numeric sequence starts over for a new local calendar date. Prior dates
are retained to prevent duplicates when the clock moves back. A calendar-day
reset is not a rolling 24-hour uniqueness guarantee. The recovered adapter
supports IDs 0..254; exhaustion fails before deletion rather than recycling IDs.
Clearing Super Clone's own app data would erase its journal and is outside the
supported deletion operation.

## Verification performed

Compiled all Java sources against the Android SDK jar with OpenJDK on S20.
Ran isolated journal tests in `/data/local/tmp/noa-id-fix-candidate/`: migration,
restart, empty clone list, failed attempt, midnight, clock rollback, occupied IDs,
concurrent threads, eight concurrent JVM processes, exhaustion and corruption.
All passed. Test files were retained; they contain synthetic journal entries.

Ran six controller guard tests locally:

```sh
python3 -B -m unittest discover -s clone-fix/tests -p 'test_*.py' -v
```

Ran `rotate.py` **without `--execute`** on S20. It returned the unchanged clone
list and migration floor 23. No candidate was loaded into Super Clone, no clone
creation/deletion was tested, and no full locked-screen cycle was verified.

## Remaining integration

The candidate transport supports `rotate FLOOR` on a separate root socket,
and adapter calls are serialized on its worker, matching the existing v0.3 bridge. The candidate
module has a separate ID and is disabled by default; its service additionally
requires `/data/adb/aiseq-v2/enabled` (not created during repair).
Check runtime compatibility with installed v0.3 before enabling rotations. Existing launch scripts
wake the device; secure-keyguard operation is not established by these tests.

No video generation or watermark removal is implemented or claimed here.
