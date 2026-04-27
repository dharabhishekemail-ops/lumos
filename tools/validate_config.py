#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path

def strip_meta(d): return {k:v for k,v in d.items() if k != '_meta'}

def validate(cfg: dict) -> tuple[bool,str]:
    if cfg.get('schemaVersion') != 1: return False,'schemaVersion must be 1'
    if 'configVersion' not in cfg: return False,'missing configVersion'
    p=cfg.get('payload')
    if not isinstance(p,dict): return False,'payload must be object'
    for k in ('featureFlags','transport','rateLimits','legalCopyVersion'):
        if k not in p: return False,f'payload missing {k}'
    rl=p.get('rateLimits',{})
    try:
        iph=int(rl.get('interestPerHour',0)); mpm=int(rl.get('messagesPerMinute',0))
    except Exception:
        return False,'rate limits must be integers'
    if not (1 <= iph <= 200): return False,'rateLimits.interestPerHour out of range [1,200]'
    if not (1 <= mpm <= 200): return False,'rateLimits.messagesPerMinute out of range [1,200]'
    sig=cfg.get('signature')
    if not isinstance(sig,dict): return False,'missing signature object'
    for k in ('alg','keyId','sigB64'):
        if k not in sig: return False,f'signature missing {k}'
    if sig.get('alg') != 'ed25519': return False,'signature.alg must be ed25519'
    return True,'ok'

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--config', required=True, type=Path)
    ap.add_argument('--schema', default='schemas/signed-config.schema.json')
    args=ap.parse_args()
    cfg=strip_meta(json.loads(args.config.read_text()))
    ok,msg=validate(cfg)
    print(('PASS ' if ok else 'FAIL ') + msg)
    return 0 if ok else 2
if __name__ == '__main__':
    raise SystemExit(main())
