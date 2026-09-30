"""Replace only the quiet-control class in a fingerprinted running Spring Boot JAR.

This release preserves the deployed speech implementation, dependencies and resources.
Compile and test the task source first; the source must match the supplied commit.
"""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile


PREFIX = "BOOT-INF/classes/com/kj/stackchan/voiceaction/VoiceActionCoordinator"
SOURCE = "server/src/main/java/com/kj/stackchan/voiceaction/VoiceActionCoordinator.java"
PROVENANCE = "META-INF/stackchan-device-quiet-release.json"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--baseline-sha256", required=True)
    parser.add_argument("--classes", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if head != args.commit:
        raise ValueError("Source commit differs from HEAD")
    subprocess.run(["git", "diff", "--quiet", "HEAD", "--", SOURCE], cwd=repo, check=True)
    if digest(args.baseline.read_bytes()) != args.baseline_sha256.lower():
        raise ValueError("Baseline JAR fingerprint differs")
    if args.output.resolve() == args.baseline.resolve():
        raise ValueError("Output must not overwrite the baseline")
    class_files = list(args.classes.glob("VoiceActionCoordinator*.class"))
    if not class_files or not (args.classes / "VoiceActionCoordinator.class").is_file():
        raise ValueError("Compiled coordinator classes are missing")
    replacements = {PREFIX.rsplit("/", 1)[0] + "/" + p.name: p.read_bytes() for p in class_files}
    provenance = {
        "sourceCommit": head,
        "sourceSha256": digest((repo / SOURCE).read_bytes()),
        "baselineSha256": args.baseline_sha256.lower(),
        "replacementSha256": {name: digest(data) for name, data in replacements.items()},
    }
    with zipfile.ZipFile(args.baseline) as baseline:
        names = baseline.namelist()
        if len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("Unexpected baseline class layout")
        if PROVENANCE in names:
            raise ValueError("Baseline already contains this release marker")
        with tempfile.NamedTemporaryFile(dir=args.output.parent, suffix=".unverified", delete=False) as file:
            temporary = Path(file.name)
        try:
            with zipfile.ZipFile(temporary, "w") as output:
                for entry in baseline.infolist():
                    # zipfile mutates header offsets when writing; keep the baseline index intact.
                    output.writestr(copy.copy(entry), replacements.get(entry.filename, baseline.read(entry.filename)))
                output.writestr(PROVENANCE, json.dumps(provenance, sort_keys=True))
            with zipfile.ZipFile(temporary) as output:
                if set(output.namelist()) != set(names) | {PROVENANCE}:
                    raise ValueError("Candidate entry set differs")
                for name in names:
                    expected = replacements.get(name, baseline.read(name))
                    if output.read(name) != expected:
                        raise ValueError("Candidate altered an unrelated JAR entry")
            temporary.replace(args.output)
        finally:
            temporary.unlink(missing_ok=True)
    print(json.dumps({"sourceCommit": head, "jarSha256": digest(args.output.read_bytes()),
                      "replacementClasses": len(replacements), "otherEntriesPreserved": True}))


if __name__ == "__main__":
    main()
