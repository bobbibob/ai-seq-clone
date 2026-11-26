#!/data/data/com.termux/files/usr/bin/python3
"""Candidate controller. Default is read-only; never install over existing scripts."""
import argparse
import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import time

HOME = Path('/data/data/com.termux/files/home')
TERMUX = '/data/data/com.termux/files/usr/bin'
CTL = '/data/adb/modules/noa_clone_bridge_ids_candidate/bin/noa-clonectl'
AI = '/data/adb/modules/noa_ai_image_bridge/bin/ai-image'
DATA = Path('/data/adb/aiseq-v2')
OLD_REGISTRY = Path('/data/adb/aiseq/registry')


class GenerationActive(RuntimeError):
    pass


def rotation_enabled():
    return (DATA / 'enabled').exists()


def run(args, **kwargs):
    kwargs.setdefault('timeout', 180)
    result = subprocess.run(args, text=True, capture_output=True, **kwargs)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or str(args))
    value = result.stdout.strip()
    if value.startswith('ERROR'):
        raise RuntimeError(value)
    return value


def ctl(command):
    unfreeze('com.polestar.super.clone:x')
    return run([CTL, *command.split()], timeout=150 if command.startswith('rotate ') else 25)


def unfreeze(package):
    result = subprocess.run(['/system/bin/pidof', package], capture_output=True, text=True)
    for pid in result.stdout.split():
        subprocess.run(['/system/bin/cmd', 'activity', 'unfreeze', pid], capture_output=True, timeout=5)


def ensure_host():
    result = subprocess.run(['/system/bin/pidof', 'com.polestar.super.clone:x'], capture_output=True)
    if result.returncode != 0:
        run(['/system/bin/am', 'start', '-W', '-n',
             'com.polestar.super.clone/com.polestar.superclone.component.activity.HomeActivity'])
        time.sleep(5)


def clones():
    output = ctl('list')
    if output == 'none':
        return []
    rows = []
    for line in output.splitlines():
        match = re.fullmatch(r'id=(\d+) name=(.+)', line)
        if not match:
            raise RuntimeError('Unrecognized clone list; refusing rotation')
        rows.append((int(match[1]), match[2]))
    return rows


def migration_floor(rows, day):
    high = max((row[0] for row in rows), default=-1)
    if OLD_REGISTRY.exists():
        for line in OLD_REGISTRY.read_text().splitlines():
            match = re.fullmatch(r'(\d+) \+(\d{8})_\d+ \S+', line)
            if not match:
                raise RuntimeError('Historical registry is malformed; migration stopped')
            if match[2] == day.replace('-', ''):
                high = max(high, int(match[1]))
    return high


def quota(kind):
    pids = run(['/system/bin/pidof', 'com.technosub.unlimitedaiimagen']).split()
    active = [pid for pid in pids if int(Path('/proc', pid, 'oom_score_adj').read_text()) < 500]
    if len(active) != 1:
        raise RuntimeError('Cannot identify a single active generator process')
    unfreeze('com.technosub.unlimitedaiimagen')
    raw = run([AI, 'request', '--pid', active[0]], input=json.dumps({'kind': kind, 'op': 'status'}), timeout=20)
    result = json.loads(raw)
    total = {'video': 10, 'edit': 2}[kind]
    if (result.get('ok') is not True or result.get('error') is not None
            or result.get('kind') != kind or type(result.get('remaining')) is not int
            or result.get('daily_max') != total or not 0 <= result['remaining'] <= total):
        raise RuntimeError('Invalid or unavailable quota; no clone operations')
    if result.get('generating') is True:
        raise GenerationActive('Generation is active; rotation postponed')
    return active[0], result['remaining'], total


def generation_busy():
    result = subprocess.run(
        ['/system/bin/pgrep', '-f',
         r'video-orig\.sh|image-orig\.sh|noa-image\.py|noa-ready\.py|noa-i2i-job|launch-last\.py|run-q[0-9]'],
        capture_output=True, text=True)
    if result.returncode not in (0, 1):
        raise RuntimeError('Cannot check active generation jobs')
    return result.returncode == 0


def launch(kind):
    env = dict(os.environ, HOME=str(HOME), PATH=TERMUX + ':/system/bin')
    python = TERMUX + '/python3'
    power = run(['/system/bin/dumpsys', 'power'])
    was_asleep = 'mWakefulness=Asleep' in power or 'mWakefulness=Dozing' in power
    try:
        # Recreate HomeActivity to discard tiles for a clone just deleted by rotation.
        run(['/system/bin/input', 'keyevent', 'KEYCODE_WAKEUP'])
        run(['/system/bin/wm', 'dismiss-keyguard'])
        run(['/system/bin/am', 'start', '-W', '-f', '0x10008000', '-n',
             'com.polestar.super.clone/com.polestar.superclone.component.activity.HomeActivity'])
        run([python, str(HOME / 'launch-last.py')], env=env)
        # Do not report success just because a tile was tapped: wait for the generator.
        run([python, str(HOME / 'noa-ready.py'), kind], env=env)
    finally:
        if was_asleep:
            run(['/system/bin/input', 'keyevent', 'KEYCODE_SLEEP'])


def rotate(kind):
    if generation_busy():
        return 'Generation job active; postponed'
    first = quota(kind)
    if first[1] != 0:
        return 'Quota remaining: %s/%s' % first[1:]
    time.sleep(5)
    if quota(kind) != first:
        raise RuntimeError('Quota or active process changed; rotation postponed')
    if generation_busy():
        return 'Generation job started; postponed'
    try:
        quota('edit' if kind == 'video' else 'video')
    except GenerationActive:
        return 'Other generator is active; postponed'
    except RuntimeError:
        pass  # Only the visible route exposes a ViewModel in this app.
    if not rotation_enabled():
        return 'Disabled; no rotation'
    if ctl('capabilities') != 'persistent-daily-ids-v1 rotate-all-v1':
        raise RuntimeError('Installed bridge lacks durable IDs; no deletion attempted')
    day = datetime.date.today().isoformat()
    floor = migration_floor(clones(), day)
    # The bridge durably reserves the ID, then deletes ALL target clones, then creates.
    output = ctl('rotate %d' % floor)
    match = re.fullmatch(r'created id=(\d+) name=(.+)', output)
    if not match:
        raise RuntimeError('Creation not confirmed; launch stopped')
    expected = (int(match[1]), match[2])
    if clones() != [expected]:
        raise RuntimeError('Unexpected clone list after rotation; launch stopped')
    # Save recovery intent before UI launch; failed launches must retry this clone,
    # never destroy it and reserve another ID.
    pending = DATA / 'pending-launch.json'
    with pending.open('w') as handle:
        json.dump({'id': expected[0], 'name': expected[1], 'kind': kind}, handle)
        handle.flush()
        os.fsync(handle.fileno())
    launch(kind)
    pending.rename(DATA / 'last-successful-launch.json')
    return output + '; generator ready'


def monitor(kind):
    kinds = ['video', 'edit'] if kind == 'both' else [kind]
    readings = []
    for selected in kinds:
        try:
            reading = quota(selected)
            readings.append((selected, reading))
            print('%s remaining=%d/%d' % (selected, reading[1], reading[2]), flush=True)
        except GenerationActive:
            return 'Generator is active; postponed'
        except Exception as error:
            # A missing/uninitialized ViewModel is NOT a zero quota.
            print('%s quota unavailable: %s' % (selected, error), flush=True)
    for selected, reading in readings:
        if reading[1] == 0:
            return rotate(selected)
    return 'No confirmed exhausted quota'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kind', choices=['video', 'edit', 'both'], default='both')
    parser.add_argument('--quotas-only', action='store_true', help='Read both quotas without rotating')
    parser.add_argument('--execute', action='store_true', help='Enable actual rotation (NOT used during repair)')
    parser.add_argument('--watch', action='store_true')
    parser.add_argument('--resume', action='store_true', help='Open the sole current clone once if no generator is running')
    parser.add_argument('--interval', type=int, default=30)
    args = parser.parse_args()
    if args.interval < 10:
        parser.error('Minimum polling interval is 10 seconds')
    if args.quotas_only:
        for kind in (['video', 'edit'] if args.kind == 'both' else [args.kind]):
            try:
                pid, remaining, total = quota(kind)
                print(json.dumps({'kind': kind, 'pid': pid, 'remaining': remaining, 'daily_max': total}))
            except Exception as error:
                print(json.dumps({'kind': kind, 'error': str(error)}))
        return
    if not args.execute:
        rows = clones()
        print(json.dumps({'read_only': True, 'clones': rows,
                          'migration_floor': migration_floor(rows, datetime.date.today().isoformat()),
                          'action': 'reserve internal ID, delete all target clones, create, launch, wait ready'},
                         ensure_ascii=False, indent=2))
        return
    DATA.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (DATA / 'controller.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        (DATA / 'controller.pid').write_text(str(os.getpid()))
        resume_pending = args.resume
        while True:
            try:
                if not (DATA / 'enabled').exists():
                    if not args.watch:
                        return
                    time.sleep(args.interval)
                    continue
                if resume_pending:
                    running = subprocess.run(['/system/bin/pidof', 'com.technosub.unlimitedaiimagen'], capture_output=True)
                    if running.returncode != 0:
                        ensure_host()
                        if len(clones()) == 1:
                            launch('video')
                    resume_pending = False
                pending = DATA / 'pending-launch.json'
                if pending.exists():
                    state = json.loads(pending.read_text())
                    if clones() != [(state['id'], state['name'])]:
                        raise RuntimeError('Pending clone changed; manual recovery required')
                    launch(state['kind'])
                    pending.rename(DATA / 'last-successful-launch.json')
                    print('Pending clone launched; generator ready', flush=True)
                else:
                    message = monitor(args.kind)
                    print(message, flush=True)
                    (DATA / 'status.json').write_text(json.dumps({'message': message, 'updated': time.time()}))
            except Exception as error:
                print('Stopped operation: ' + str(error), flush=True)
                (DATA / 'status.json').write_text(json.dumps({'message': str(error), 'updated': time.time()}))
                if not args.watch:
                    raise SystemExit(1)
            if not args.watch:
                return
            time.sleep(args.interval)


if __name__ == '__main__':
    main()
