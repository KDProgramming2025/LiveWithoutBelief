#!/usr/bin/env python3
"""
Triage JUnit XML results and emit:
- build/reports/triage/summary.md: Markdown with totals and failing tests with messages
- build/reports/triage/failures.txt: One failing test per line as ClassName#methodName

Usage: python3 scripts/triage_junit.py [glob ...]
If no glob provided, defaults to app/build/test-results/testDebugUnitTest/*.xml

Exit code is always 0 (triage should not alter job outcome).
"""

from __future__ import annotations
import glob
import os
import sys
import xml.etree.ElementTree as ET


def parse_file(path: str):
    try:
        tree = ET.parse(path)
    except Exception as e:
        return {"path": path, "error": f"Failed to parse XML: {e}"}
    root = tree.getroot()
    cases = []
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    # Support either <testsuite> or <testsuites>
    suites = []
    if root.tag == "testsuite":
        suites = [root]
    elif root.tag == "testsuites":
        suites = list(root)
    for suite in suites:
        for k in totals.keys():
            v = suite.attrib.get(k)
            if v is not None and str(v).isdigit():
                totals[k] += int(v)
        for tc in suite.findall("testcase"):
            classname = tc.attrib.get("classname", "")
            name = tc.attrib.get("name", "")
            time = float(tc.attrib.get("time", 0) or 0)
            failure_el = tc.find("failure") or tc.find("error")
            skipped_el = tc.find("skipped")
            status = "passed"
            msg = None
            details = None
            if skipped_el is not None:
                status = "skipped"
                msg = skipped_el.attrib.get("message")
            if failure_el is not None:
                status = "failed" if failure_el.tag == "failure" else "error"
                msg = failure_el.attrib.get("message")
                details = (failure_el.text or "").strip()
            cases.append({
                "classname": classname,
                "name": name,
                "time": time,
                "status": status,
                "message": msg,
                "details": details,
            })
    return {"path": path, "totals": totals, "cases": cases}


def main():
    globs = sys.argv[1:] or [
        os.path.join("app", "build", "test-results", "testDebugUnitTest", "*.xml")
    ]
    files = []
    for pattern in globs:
        files.extend(glob.glob(pattern))
    results = [parse_file(p) for p in files]

    all_cases = []
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for r in results:
        t = r.get("totals")
        if t:
            for k in totals.keys():
                totals[k] += int(t.get(k, 0))
        for c in r.get("cases", []):
            all_cases.append(c)

    failing = [c for c in all_cases if c["status"] in ("failed", "error")]

    out_dir = os.path.join("build", "reports", "triage")
    os.makedirs(out_dir, exist_ok=True)
    summary_path = os.path.join(out_dir, "summary.md")
    failures_list_path = os.path.join(out_dir, "failures.txt")

    with open(summary_path, "w", encoding="utf-8") as f:
        f.write("# Test Failure Triage Summary\n\n")
        f.write(
            f"Total: {totals['tests']} • Failed: {totals['failures']} • Errors: {totals['errors']} • Skipped: {totals['skipped']}\n\n"
        )
        if failing:
            f.write("## Failing tests\n\n")
            for c in failing:
                ident = f"{c['classname']}#{c['name']}"
                msg = c.get("message") or ""
                f.write(f"- {ident} — {c['status']}\n")
                if msg:
                    f.write(f"  - Message: {msg}\n")
        else:
            f.write("No failing tests detected.\n")

    with open(failures_list_path, "w", encoding="utf-8") as f:
        for c in failing:
            f.write(f"{c['classname']}#{c['name']}\n")

    # Always exit 0 (triage is informational)
    return 0


if __name__ == "__main__":
    sys.exit(main())
