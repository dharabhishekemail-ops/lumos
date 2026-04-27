#!/usr/bin/env python3
import argparse, os, json, hashlib, pathlib, shutil, subprocess, time, zipfile, datetime

import xml.etree.ElementTree as ET

def _junit_summary(xml_paths):
    total_tests=total_failures=total_errors=total_skipped=0
    total_time=0.0
    suite_count=0
    for p in xml_paths:
        try:
            tree=ET.parse(p)
            root=tree.getroot()
        except Exception:
            continue
        if root.tag == 'testsuite':
            suites=[root]
        elif root.tag == 'testsuites':
            suites=list(root.findall('testsuite'))
        else:
            suites=[]
        for s in suites:
            suite_count += 1
            total_tests += int(s.attrib.get('tests',0))
            total_failures += int(s.attrib.get('failures',0))
            total_errors += int(s.attrib.get('errors',0))
            total_skipped += int(s.attrib.get('skipped',0))
            try:
                total_time += float(s.attrib.get('time',0.0))
            except Exception:
                pass
    status = 'PASS' if (total_failures==0 and total_errors==0) else 'FAIL'
    return {
        'suites': suite_count,
        'tests': total_tests,
        'failures': total_failures,
        'errors': total_errors,
        'skipped': total_skipped,
        'time_seconds': round(total_time,3),
        'status': status
    }

def _xcresult_summary(xcresult_path: pathlib.Path):
    try:
        r=subprocess.run(['xcrun','xcresulttool','get','--format','json','--path',str(xcresult_path)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=False)
        if r.returncode!=0:
            return {'status':'UNKNOWN','note':'xcresulttool failed','details':(r.stdout or '').strip()[:500]}
        data=json.loads(r.stdout)
    except FileNotFoundError:
        return {'status':'UNAVAILABLE','note':'xcrun not available (likely not macOS)'}
    except Exception as e:
        return {'status':'UNKNOWN','note':f'xcresult parse error: {e}'}
    metrics={}
    if isinstance(data, dict) and 'metrics' in data and isinstance(data['metrics'], dict):
        m=data['metrics']
        for k in ['testsCount','failureCount','testFailureCount','skippedCount','duration']:
            if k in m:
                v=m[k]
                metrics[k]=v.get('_value') if isinstance(v, dict) and '_value' in v else v
    def walk(obj):
        if isinstance(obj, dict):
            for k,v in obj.items():
                if k in ['testsCount','failureCount','testFailureCount','skippedCount','duration'] and k not in metrics:
                    metrics[k]=v.get('_value') if isinstance(v, dict) and hasattr(v,'get') and '_value' in v else (v['_value'] if isinstance(v, dict) and '_value' in v else v)
                walk(v)
        elif isinstance(obj, list):
            for it in obj: walk(it)
    walk(data)
    def _to_int(x):
        try: return int(x)
        except Exception: return 0
    tests=_to_int(metrics.get('testsCount',0))
    failures=_to_int(metrics.get('failureCount', metrics.get('testFailureCount',0)))
    skipped=_to_int(metrics.get('skippedCount',0))
    dur=metrics.get('duration', None)
    try: dur=float(dur)
    except Exception: dur=None
    status='PASS' if failures==0 else 'FAIL'
    return {'status':status,'tests':tests,'failures':failures,'skipped':skipped,'duration_seconds':dur,'note':'best-effort via xcresulttool'}

def sha256_file(p: pathlib.Path) -> str:
    h=hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024), b""):
            h.update(chunk)
    return h.hexdigest()

def run(cmd, cwd=None, timeout=60):
    try:
        env = os.environ.copy()
        env.setdefault("PYTEST_DISABLE_PLUGIN_AUTOLOAD", "1")
        r=subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=False, timeout=timeout, env=env)
        return r.returncode, r.stdout
    except subprocess.TimeoutExpired as e:
        return 124, (e.stdout or "") + "\nTIMEOUT"
    except (FileNotFoundError, PermissionError) as e:
        return 127, str(e)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--out", default="evidence_bundle.zip")
    ap.add_argument("--fixtures", default="fixtures")
    args=ap.parse_args()

    root=pathlib.Path(".").resolve()
    tmp=root/"_evidence_tmp"
    if tmp.exists(): shutil.rmtree(tmp)
    tmp.mkdir()

    meta={
        "created_utc": datetime.datetime.utcnow().isoformat()+"Z",
        "workspace_root": str(root),
    }

    # Git metadata if available
    rc, out = run(["git","rev-parse","HEAD"])
    if rc==0: meta["git_head"]=out.strip()
    rc, out = run(["git","status","--porcelain"])
    if rc==0: meta["git_dirty"] = bool(out.strip())
    # Tool versions
    for tool in [["python3","--version"],["java","-version"],["xcodebuild","-version"]]:
        rc, o = run(tool)
        meta["tool_"+tool[0]] = o.strip()

    # Generate and capture Python dependency lock/evidence
    dep_rc, dep_out = run(["python3", "tools/generate_dependency_lock.py"])
    meta["python_dependency_lock_exitcode"] = dep_rc
    meta["python_dependency_lock_output"] = dep_out.strip()[:1000]

    (tmp/"meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")

    # Run conformance runner
    rc, out = run(["python3", "tools/conformance_runner.py", "--fixtures", args.fixtures, "--schemas", "schemas", "--out", "docs/conformance_report.json"])
    (tmp/"conformance.txt").write_text(out, encoding="utf-8")
    (tmp/"conformance_exitcode.txt").write_text(str(rc), encoding="utf-8")

    # Collect Android unit test reports if present
    android_reports = list((root/"android").rglob("build/reports/tests"))
    if android_reports:
        ar_dir=tmp/"android_test_reports"
        ar_dir.mkdir()
        for p in android_reports:
            dest = ar_dir/p.relative_to(root/"android")
            dest.parent.mkdir(parents=True, exist_ok=True)
            if p.is_dir():
                shutil.copytree(p, dest, dirs_exist_ok=True)
            else:
                shutil.copy2(p, dest)

    
    # Collect Android JUnit XML results if present
    junit_xml = list((root/"android").rglob("build/test-results/**/*.xml"))
    if junit_xml:
        jr_dir = tmp/"android_junit_xml"
        jr_dir.mkdir()
        for p in junit_xml:
            # keep structure under android/
            dest = jr_dir/p.relative_to(root/"android")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)

    # Collect iOS xcodebuild result bundle from build/ios if present
    ios_bundle = root/"build"/"ios"/"TestResults.xcresult"
    if ios_bundle.exists():
        ir_dir = tmp/"ios_test_results"
        ir_dir.mkdir()
        dest = ir_dir/"TestResults.xcresult"
        if ios_bundle.is_dir():
            shutil.copytree(ios_bundle, dest, dirs_exist_ok=True)
        else:
            shutil.copy2(ios_bundle, dest)

# Collect iOS test results if present (xcresult is mac-only)
    ios_results = list((root/"ios").rglob("*.xcresult"))
    if ios_results:
        ir_dir=tmp/"ios_test_results"
        ir_dir.mkdir()
        for p in ios_results:
            dest = ir_dir/p.relative_to(root/"ios")
            dest.parent.mkdir(parents=True, exist_ok=True)
            if p.is_dir():
                shutil.copytree(p, dest, dirs_exist_ok=True)
            else:
                shutil.copy2(p, dest)

    # Generate evidence summary (human-readable)
    summary_lines=[]
    summary_lines.append('# Evidence Summary')
    summary_lines.append('')
    summary_lines.append(f"Generated (UTC): {meta.get('created_utc','')}")
    summary_lines.append('')
    # Android JUnit summary
    if junit_xml:
        js=_junit_summary(junit_xml)
        summary_lines.append('## Android Unit Tests (JUnit XML)')
        summary_lines.append(f"Status: **{js['status']}**")
        summary_lines.append(f"Suites: {js['suites']}  ")
        summary_lines.append(f"Tests: {js['tests']}  ")
        summary_lines.append(f"Failures: {js['failures']}  ")
        summary_lines.append(f"Errors: {js['errors']}  ")
        summary_lines.append(f"Skipped: {js['skipped']}  ")
        summary_lines.append(f"Duration (s): {js['time_seconds']}")
        summary_lines.append('')
    else:
        summary_lines.append('## Android Unit Tests (JUnit XML)')
        summary_lines.append('Status: **NOT FOUND** (run `make android-test`)')
        summary_lines.append('')
    # iOS xcresult summary
    summary_lines.append('## iOS Unit Tests (XCTest xcresult)')
    if ios_bundle.exists():
        xs=_xcresult_summary(ios_bundle)
        summary_lines.append(f"Status: **{xs.get('status','UNKNOWN')}**")
        if 'tests' in xs: summary_lines.append(f"Tests: {xs['tests']}  ")
        if 'failures' in xs: summary_lines.append(f"Failures: {xs['failures']}  ")
        if 'skipped' in xs: summary_lines.append(f"Skipped: {xs['skipped']}  ")
        if xs.get('duration_seconds') is not None: summary_lines.append(f"Duration (s): {xs['duration_seconds']}")
        summary_lines.append(f"Note: {xs.get('note','')}")
        summary_lines.append('')
    else:
        summary_lines.append('Status: **NOT FOUND** (run `make ios-test` on macOS)')
        summary_lines.append('')
    # Conformance
    summary_lines.append('## Interop Conformance')
    summary_lines.append(f"conformance_runner exit code: {rc}")
    summary_lines.append('See `conformance.txt` inside evidence bundle for details.')
    summary_lines.append('')
    summary_md='\n'.join(summary_lines)+"\n"
    # Write into repo docs/ and also include in evidence bundle
    docs_dir = root/'docs'
    docs_dir.mkdir(exist_ok=True)
    (docs_dir/'evidence_summary.md').write_text(summary_md, encoding='utf-8')
    (tmp/'evidence_summary.md').write_text(summary_md, encoding='utf-8')

# Checksums for key specs and schemas (if present)
    chk={}
    for p in (root/"schemas").rglob("*"):
        if p.is_file():
            chk[str(p.relative_to(root))]=sha256_file(p)
    for p in (root/"fixtures").rglob("*.json"):
        chk[str(p.relative_to(root))]=sha256_file(p)
    (tmp/"checksums.json").write_text(json.dumps(chk, indent=2), encoding="utf-8")

    # Include dependency lock files and selected acceptance docs if present
    evidence_docs = [
        root/"docs"/"python_dependency_lock.json",
        root/"docs"/"python_dependency_lock.txt",
        root/"docs"/"RC2_ACCEPTANCE_CHECKLIST.md",
        root/"docs"/"TEST_REPORT.md",
        root/"docs"/"RTM.md",
        root/"docs"/"ON_DEVICE_TEST_PLAN.md",
        root/"docs"/"ROLLOUT_PLAN.md",
        root/"docs"/"INCIDENT_RUNBOOK.md",
        root/"docs"/"PRODUCTION_READINESS_PLAN.md",
    ]
    ed = tmp/"docs"
    ed.mkdir(exist_ok=True)
    for doc in evidence_docs:
        if doc.exists():
            shutil.copy2(doc, ed/doc.name)

    # Zip
    outzip = root/args.out
    if outzip.exists(): outzip.unlink()
    with zipfile.ZipFile(outzip, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for p in tmp.rglob("*"):
            z.write(p, p.relative_to(tmp))
    shutil.rmtree(tmp)
    print(f"Wrote {outzip}")

if __name__=="__main__":
    main()