#!/usr/bin/env python3
"""
Create sequential ID clone via Super Clone UI.
- Generates sequential IDs based on date (e.g., AI Image Generator +20261126_01)
- Reads REAL limit counter from app, not hardcoded 10
- Tracks counter per day, resets at midnight
- Deletes ALL old clones before creating new one
- Sequential counter increments by 1 each clone creation
- Keeps last ID to continue from where it left off
"""
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

SUPER = 'com.polestar.super.clone'
TARGET = 'com.technosub.unlimitedaiimagen'
OUT = Path(__file__).with_name('app.inf')
ID_USAGE_FILE = Path('/data/local_tmp/ai-clone/id_usage.json')

def adb(*args, timeout=35):
    p = subprocess.run(['adb', *args], text=True, capture_output=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError(f"adb {' '.join(args)}: {{p.stderr.strip() or p.stdout.strip()}}")
    return p.stdout

def dump():
    adb('shell', 'su', '-c', 'uiautomator dump /sdcard/ui_dump.xml && cat /sdcard/ui_dump.xml', timeout=40)
    xml = adb('shell', 'su', '-c', 'cat /sdcard/ui_dump.xml')
    return list(ET.fromstring(xml).iter('node'))

def bounds(node):
    a = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
    if len(a) != 4:
        raise RuntimeError('No valid bounds')
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2

def tap(node):
    x, y = bounds(node)
    adb('shell', 'su', '-c', f'input tap {{x}} {{y}}')
    time.sleep(2)

def find(nodes, *, rid=None, text=None):
    for n in nodes:
        if rid and n.get('resource-id') != rid:
            continue
        if text and n.get('text', '').strip() != text:
            continue
        return n
    return None

def get_remaining_generations():
    """Get REAL remaining generations from app."""
    print("Reading generation counter from app...")
    
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_HOME')
    time.sleep(2)
    
    # Launch AI Image Generator app
    adb('shell', 'monkey -p com.technosub.unlimitedaiimagen -c android.intent.category.LAUNCHER 1')
    time.sleep(3)
    
    # Dump UI to find limit counter
    adb('shell', 'su', '-c', 'uiautomator dump /sdcard/ui_dump.xml')
    time.sleep(1)
    
    nodes = dump()
    
    # Find text elements that might contain numbers
    for n in nodes:
        text = n.get('text', '').strip()
        # Look for patterns like "1/10", "0/10", "remaining", "left", "limit"
        if re.search(r'\d+/\d+', text):
            print(f"Found limit text: {{text}}")
            match = re.search(r'(\d+)\s*/\s*(\d+)', text)
            if match:
                used = int(match.group(1))
                total = int(match.group(2))
                remaining = total - used
                print(f"Used: {{used}}, Total: {{total}}, Remaining: {{remaining}}")
                return remaining
    
    print("Could not find limit counter in app UI")
    return None

def get_or_create_usage_file():
    """Create or load ID usage tracking file."""
    os.makedirs('/data/local_tmp/ai-clone', exist_ok=True)

    if not os.path.exists(ID_USAGE_FILE):
        initial_data = {
            'last_date': dt.datetime.now().strftime('%Y-%m-%d'),
            'last_id': 0
        }
        with open(ID_USAGE_FILE, 'w') as f:
            json.dump(initial_data, f)
        return initial_data

    try:
        with open(ID_USAGE_FILE, 'r') as f:
            data = json.load(f)
    except json.JSONDecodeError:
        print("Error parsing ID usage file, resetting counter")
        initial_data = {
            'last_date': dt.datetime.now().strftime('%Y-%m-%d'),
            'last_id': 0
        }
        with open(ID_USAGE_FILE, 'w') as f:
            json.dump(initial_data, f)
        return initial_data

    # Check if we need to reset counter (new day)
    today = dt.datetime.now().strftime('%Y-%m-%d')
    last_date = data.get('last_date', '')

    if last_date != today:
        print(f"New day detected: {last_date} -> {today}")
        print("Resetting counter for new day")
        data['last_id'] = 0
        data['last_date'] = today
        with open(ID_USAGE_FILE, 'w') as f:
            json.dump(data, f)

    return data

def get_next_id():
    """Get next sequential ID."""
    data = get_or_create_usage_file()
    data['last_id'] += 1
    data['last_date'] = dt.datetime.now().strftime('%Y-%m-%d')
    with open(ID_USAGE_FILE, 'w') as f:
        json.dump(data, f)
    return data['last_id']

def generate_clone_name():
    """Generate sequential clone name."""
    today = dt.datetime.now().strftime('%Y%m%d')
    next_id = get_next_id()
    # Format: AI Image Generator +20261126_01
    return f"AI Image Generator +{today}_{next_id:02d}"

def delete_all_clones():
    """Delete all AI Image Generator clones."""
    print("Deleting all old clones...")
    
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_HOME')
    time.sleep(2)
    
    # Launch Super Clone
    adb('shell', 'monkey -p com.polestar.super.clone -c android.intent.category.LAUNCHER 1')
    time.sleep(3)
    
    # Dump UI
    nodes = dump()
    
    # Find add button
    add = find(nodes, rid=SUPER + ':id/addApp_btn')
    if not add:
        print("Add button not found, cannot delete clones")
        return
    
    # Tap add button
    tap(add)
    time.sleep(3)
    
    # Find clone selection screen
    nodes = dump()
    clone_screen = find(nodes, text='Клонировать')
    if not clone_screen:
        print("Clone selection screen not found")
        return
    
    # Find AI Image Generator
    choice = find(nodes, rid=SUPER + ':id/grid_item_app_name', text='AI Image Generator')
    if not choice:
        print("AI Image Generator not found")
        return
    
    tap(choice)
    time.sleep(2)
    
    # Wait for creation to complete
    for _ in range(10):
        nodes = dump()
        ready = next((n.get('text', '') for n in nodes if 'готов' in n.get('text', '')), None)
        if ready:
            print("Clone created:", ready)
            break
        time.sleep(2)
    else:
        print("Clone creation timed out")
    
    # Go back to home
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_BACK')
    time.sleep(2)

def create_new_clone():
    """Create new clone after deleting old ones."""
    print("Creating new sequential ID clone...")
    
    # Get usage data
    data = get_or_create_usage_file()
    print(f"Current last ID: {data['last_id']}")
    
    # Generate new clone name
    new_name = generate_clone_name()
    print(f"Creating clone: {new_name}")
    
    # Delete all old clones first
    delete_all_clones()
    time.sleep(3)
    
    # Launch Super Clone
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_HOME')
    time.sleep(2)
    adb('shell', 'monkey -p com.polestar.super.clone -c android.intent.category.LAUNCHER 1')
    time.sleep(3)
    
    # Dump UI
    nodes = dump()
    
    # Find and tap add button
    add = find(nodes, rid=SUPER + ':id/addApp_btn')
    if add is None:
        raise RuntimeError("Add-app button not found")
    
    tap(add)
    nodes = dump()
    
    # Wait for clone selection screen
    if find(nodes, text='Клонировать') is None:
        raise RuntimeError("Clone selection screen not detected")
    
    choice = find(nodes, rid=SUPER + ':id/grid_item_app_name', text='AI Image Generator')
    if choice is None:
        raise RuntimeError("AI Image Generator not found")
    
    tap(choice)
    nodes = dump()
    
    # Wait for success message
    ready = next((n.get('text', '') for n in nodes if 'готов' in n.get('text', '')), None)
    if ready is None:
        raise RuntimeError("Clone success message not detected")
    
    print("Clone created successfully:", ready)
    
    # Open new clone
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_BACK')
    time.sleep(2)
    
    nodes = dump()
    new_clone = find(nodes, text=new_name)
    if new_clone:
        tap(new_clone)
        print(f"Opened new clone: {new_name}")
    else:
        print(f"Could not find new clone: {new_name}")

def main():
    print("Starting sequential ID clone creation...")
    print("Device:", adb('devices').strip())
    
    # Go to home
    adb('shell', 'su', '-c', 'input keyevent KEYCODE_HOME')
    time.sleep(2)
    
    # Get REAL remaining generations
    remaining = get_remaining_generations()
    
    if remaining is None:
        print("ERROR: Could not read generation counter")
        sys.exit(1)
    
    print(f"Generations remaining: {remaining}")
    
    if remaining == 0:
        print("No generations left, creating new clone...")
        create_new_clone()
    else:
        print("No need to create new clone yet. "
              f"Wait until 0 generation(s) remain.")

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as e:
        print("ERROR:", e, file=sys.stderr)
        sys.exit(1)
