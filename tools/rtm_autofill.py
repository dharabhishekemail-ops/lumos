#!/usr/bin/env python3
from __future__ import annotations
import argparse
from pathlib import Path

ANDROID_HINTS = {
  "protocol": "android:core-protocol/src/test:ProtocolFixtureTests",
  "media": "android:core-media/src/test:MediaPipelineTest",
  "session": "android:core-session/src/test:OrchestratorTests",
  "config": "android:core-remoteconfig/src/test:ConfigTests",
  "billing": "android:core-billing/src/test:BillingTests",
}
IOS_HINTS = {
  "protocol": "ios:Tests/LumosProtocolTests:FixtureTests,CapabilityNegotiatorTests",
  "media": "ios:Tests/LumosMediaTests:MediaPipelineTests",
  "session": "ios:Tests/LumosSessionTests:SessionOrchestratorTests,FaultSimTests",
  "config": "ios:Tests/LumosConfigTests:ConfigTests,ConfigSignatureVerifierTests",
  "billing": "ios:StoreKitManagerTests (planned)",
}

def infer(impl: str):
    s=impl.lower()
    out=[]
    if "protocol" in s or "core-protocol" in s or "lumosprotocol" in s:
        out += [ANDROID_HINTS["protocol"], IOS_HINTS["protocol"]]
    if "media" in s or "core-media" in s or "lumosmedia" in s:
        out += [ANDROID_HINTS["media"], IOS_HINTS["media"]]
    if "session" in s or "core-session" in s or "lumossession" in s:
        out += [ANDROID_HINTS["session"], IOS_HINTS["session"]]
    if "config" in s or "remoteconfig" in s or "operator" in s or "lumosconfig" in s:
        out += [ANDROID_HINTS["config"], IOS_HINTS["config"]]
    if "billing" in s or "monet" in s or "storekit" in s:
        out += [ANDROID_HINTS["billing"], IOS_HINTS["billing"]]
    ded=[]
    for x in out:
        if x not in ded: ded.append(x)
    return "; ".join(ded) + " (AUTO)" if ded else None

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rtm", required=True)
    ap.add_argument("--out", required=True)
    args=ap.parse_args()
    p=Path(args.rtm)
    lines=p.read_text(encoding="utf-8").splitlines()
    out=[]
    for line in lines:
        if not line.strip().startswith("- ") or "|" not in line:
            out.append(line); continue
        parts=[x.strip() for x in line[2:].split("|")]
        if len(parts) < 4:
            out.append(line); continue
        rid,status,impl,test = parts[0],parts[1],parts[2],parts[3]
        if test.upper()=="TBD":
            inf=infer(impl)
            if inf: test=inf
        out.append(f"- {rid} | {status} | {impl} | {test}")
    Path(args.out).write_text("\n".join(out)+"\n", encoding="utf-8")
    return 0
if __name__=="__main__":
    raise SystemExit(main())
