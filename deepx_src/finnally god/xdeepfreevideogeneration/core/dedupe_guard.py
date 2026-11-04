# -*- coding: utf-8 -*-
"""dedupe_guard.py: Bulletproof Asset Registry and Anti-Duplication Engine.

Enforces zero-repeat asset generation across all Google Flow and DeepX operations.
"""
import os, glob, json, hashlib

REGISTRY_PATH = 'elements/registry.json'

def get_registry():
    if os.path.exists(REGISTRY_PATH):
        try:
            with open(REGISTRY_PATH, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_registry(reg):
    os.makedirs(os.path.dirname(REGISTRY_PATH), exist_ok=True)
    with open(REGISTRY_PATH, 'w', encoding='utf-8') as f:
        json.dump(reg, f, ensure_ascii=False, indent=2)

def is_asset_ready(asset_id):
    cut_hits = glob.glob(f'elements/cut/{asset_id}.png')
    if cut_hits and os.path.getsize(cut_hits[0]) > 1024:
        return 'KEYED_READY'
    raw_hits = glob.glob(f'elements/raw/{asset_id}.*')
    if raw_hits and os.path.getsize(raw_hits[0]) > 1024:
        return 'RAW_EXISTS'
    reg = get_registry()
    if asset_id in reg and reg[asset_id].get('status') in ('QUEUED', 'DISPATCHED'):
        return 'IN_FLIGHT'
    return 'MISSING'

def register_dispatched(asset_id, prompt):
    reg = get_registry()
    prompt_hash = hashlib.md5(prompt.strip().encode('utf-8')).hexdigest()
    reg[asset_id] = {
        'status': 'DISPATCHED',
        'prompt_hash': prompt_hash,
        'prompt': prompt[:120]
    }
    save_registry(reg)

def mark_asset_completed(asset_id):
    reg = get_registry()
    if asset_id in reg:
        reg[asset_id]['status'] = 'COMPLETED'
        save_registry(reg)

def get_pending_assets(elements_rows):
    pending = []
    for r in elements_rows:
        aid = str(r['id']).zfill(2)
        state = is_asset_ready(aid)
        if state == 'MISSING':
            pending.append(r)
        else:
            print(f'[{aid}] Skipped generation -> status: {state}')
    return pending
