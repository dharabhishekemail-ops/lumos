#!/usr/bin/env python3
import argparse, json
from pathlib import Path
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rtm", required=True)
    ap.add_argument("--out", required=True)
    a=ap.parse_args()
    rows=[]
    for line in Path(a.rtm).read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("- "):
            parts=[p.strip() for p in line[2:].split("|")]
            if len(parts)>=2:
                rows.append({"id":parts[0],"status":parts[1],"impl":parts[2] if len(parts)>2 else "", "tests":parts[3] if len(parts)>3 else ""})
    total=len(rows)
    implemented=sum(1 for r in rows if r["status"].lower().startswith("implemented"))
    partial=sum(1 for r in rows if r["status"].lower().startswith("partial"))
    missing=total-implemented-partial
    rep={"total":total,"implemented":implemented,"partial":partial,"missing":missing,"rows":rows}
    Path(a.out).write_text(json.dumps(rep,indent=2),encoding="utf-8")
    print(json.dumps({k:rep[k] for k in ("total","implemented","partial","missing")},indent=2))
    return 0
if __name__=="__main__":
    raise SystemExit(main())
