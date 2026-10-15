"""Summarize anonymous R01 timing observations without recording speech.

Usage: python scripts/analyze-voice-quality.py samples.csv [--output report.json]
The empty checked-in CSV is a collection template, not a passing result.
Only real physical observations can satisfy a physical timing gate. Device
playback start means a codec write and is reported separately from first sound.
"""

import argparse
import csv
import json
import math
from pathlib import Path
import re
import sys


COLUMNS = (
    "sample_id", "evidence_type", "kind", "measurement_source", "condition",
    "result", "error_code", "endpoint_to_local_feedback_ms",
    "endpoint_to_playback_start_ms", "endpoint_to_first_sound_ms",
    "end_of_speech_to_endpoint_ms", "end_of_speech_to_first_sound_ms",
    "cancellation_to_silence_ms",
)
EVIDENCE_TYPES = ("REAL", "SYNTHETIC")
KINDS = ("SHORT_CHAT", "TOOL", "CANCEL")
SOURCES = ("DEVICE_MONOTONIC", "PHYSICAL_OBSERVATION")
CONDITIONS = (
    "QUIET_WAKE", "QUIET_PTT", "BACKGROUND_WAKE", "BACKGROUND_PTT",
    "LOW_VOLUME", "WORD_PAUSE", "SENTENCE_PAUSE", "NETWORK_DELAY",
    "NETWORK_FAILURE", "MEMORY_PRESSURE", "RECOVERY", "OTHER_CONTROLLED",
)
RESULTS = ("SUCCESS", "FAILED", "CANCELLED", "TIMEOUT", "MISSING")
ERROR_CODES = (
    "NO_SPEECH", "OFFLINE", "OUT_OF_MEMORY", "UPLOAD_FAILED",
    "INVALID_RESPONSE", "PLAYBACK_FAILED", "MICROPHONE_RECOVERY_FAILED",
    "ASR_UNAVAILABLE", "LLM_UNAVAILABLE", "TTS_UNAVAILABLE", "INTERNAL_ERROR",
    "TIMEOUT", "MISSING_EVIDENCE",
)
DURATION_COLUMNS = COLUMNS[7:]
PHYSICAL_COLUMNS = (
    "endpoint_to_first_sound_ms", "end_of_speech_to_endpoint_ms",
    "end_of_speech_to_first_sound_ms", "cancellation_to_silence_ms",
)
FIRST_SOUND_MINIMUM = 30
CANCEL_MINIMUM = 10
MAX_DURATION_MS = 300_000


class InvalidEvidence(ValueError):
    """Invalid schema or anonymous measurements; the error never echoes cells."""


def read_samples(path):
    """Read one exact schema, rejecting free text and mixed measurement sources."""
    samples = []
    identifiers = set()
    try:
        with Path(path).open("r", encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source, strict=True)
            if tuple(reader.fieldnames or ()) != COLUMNS:
                raise InvalidEvidence("CSV header must match the anonymous template exactly")
            for row in reader:
                line = reader.line_num
                if None in row or any(value is None for value in row.values()):
                    raise InvalidEvidence(f"line {line}: wrong number of columns")
                row = {key: value.strip() for key, value in row.items()}
                if not re.fullmatch(r"S(?:[0-9]{3}|[1-9][0-9]{3,5})", row["sample_id"]) or int(row["sample_id"][1:]) == 0:
                    raise InvalidEvidence(f"line {line}: sample_id must use anonymous S001 format")
                if row["sample_id"] in identifiers:
                    raise InvalidEvidence(f"line {line}: duplicate sample_id")
                identifiers.add(row["sample_id"])
                for column, allowed in (
                    ("evidence_type", EVIDENCE_TYPES), ("kind", KINDS),
                    ("measurement_source", SOURCES), ("condition", CONDITIONS),
                    ("result", RESULTS), ("error_code", ("",) + ERROR_CODES),
                ):
                    if row[column] not in allowed:
                        raise InvalidEvidence(f"line {line}: invalid {column}")
                if row["result"] == "SUCCESS" and row["error_code"]:
                    raise InvalidEvidence(f"line {line}: successful sample cannot have an error_code")
                for column in DURATION_COLUMNS:
                    value = row[column]
                    if value == "":
                        row[column] = None
                    elif re.fullmatch(r"[0-9]{1,6}", value) and int(value) <= MAX_DURATION_MS:
                        row[column] = int(value)
                    else:
                        raise InvalidEvidence(f"line {line}: invalid {column}")
                if row["measurement_source"] == "DEVICE_MONOTONIC":
                    if any(row[column] is not None for column in PHYSICAL_COLUMNS):
                        raise InvalidEvidence(f"line {line}: physical timing cannot use device source")
                elif row["endpoint_to_playback_start_ms"] is not None:
                    raise InvalidEvidence(f"line {line}: codec timing must use device source")
                if row["kind"] == "CANCEL":
                    if any(row[column] is not None for column in DURATION_COLUMNS[:-1]):
                        raise InvalidEvidence(f"line {line}: cancellation is a separate measurement")
                elif row["cancellation_to_silence_ms"] is not None:
                    raise InvalidEvidence(f"line {line}: silence timing requires CANCEL kind")
                endpoint = row["end_of_speech_to_endpoint_ms"]
                first = row["endpoint_to_first_sound_ms"]
                total = row["end_of_speech_to_first_sound_ms"]
                if total is not None and any(
                    value is not None and value > total for value in (endpoint, first)
                ):
                    raise InvalidEvidence(f"line {line}: inconsistent physical timing order")
                if None not in (endpoint, first, total) and abs(endpoint + first - total) > 2:
                    raise InvalidEvidence(f"line {line}: physical timing differences disagree")
                samples.append(row)
    except (csv.Error, UnicodeError) as error:
        raise InvalidEvidence("invalid UTF-8 CSV structure") from error
    return samples


def counts(samples):
    """Retain every result, condition and fixed failure code in its own cohort."""
    result_counts = {result: 0 for result in RESULTS}
    conditions = {}
    errors = {}
    for sample in samples:
        result_counts[sample["result"]] += 1
        conditions[sample["condition"]] = conditions.get(sample["condition"], 0) + 1
        if sample["result"] in ("FAILED", "TIMEOUT"):
            code = sample["error_code"] or "UNKNOWN"
            errors[code] = errors.get(code, 0) + 1
    return {
        "total_attempts": len(samples), "results": result_counts,
        "failure_codes": dict(sorted(errors.items())),
        "conditions": dict(sorted(conditions.items())),
    }


def metric(samples, column, threshold=None, percentiles=False, minimum=30):
    measured = [sample for sample in samples if sample[column] is not None]
    values = sorted(sample[column] for sample in measured)
    enough = len(values) >= minimum
    return {
        "applicable_attempts": len(samples), "measured_samples": len(values),
        "missing_samples": len(samples) - len(values), "minimum_samples": minimum,
        "p50_ms": values[math.ceil(0.50 * len(values)) - 1] if percentiles and enough else None,
        "p95_ms": values[math.ceil(0.95 * len(values)) - 1] if percentiles and enough else None,
        "max_ms": max(values) if values else None,
        "threshold_ms": threshold,
        "over_threshold_samples": sum(value > threshold for value in values) if threshold is not None else None,
        "measurements": [
            {"sample_id": sample["sample_id"], "ms": sample[column]}
            for sample in measured
        ],
    }


def per_sample_gate(summary):
    if summary["over_threshold_samples"]:
        return "FAIL"
    if summary["measured_samples"] < summary["minimum_samples"] or summary["missing_samples"]:
        return "UNKNOWN"
    return "PASS"


def analyze(samples):
    cohorts = []
    for evidence in EVIDENCE_TYPES:
        for source in SOURCES:
            for kind in KINDS:
                selected = [row for row in samples if (
                    row["evidence_type"], row["measurement_source"], row["kind"]
                ) == (evidence, source, kind)]
                if not selected:
                    continue
                successful = [row for row in selected if row["result"] == "SUCCESS"]
                durations = {}
                if kind == "CANCEL":
                    applicable_columns = ("cancellation_to_silence_ms",) if source == "PHYSICAL_OBSERVATION" else ()
                elif source == "DEVICE_MONOTONIC":
                    applicable_columns = ("endpoint_to_local_feedback_ms", "endpoint_to_playback_start_ms")
                else:
                    applicable_columns = ("endpoint_to_local_feedback_ms", "endpoint_to_first_sound_ms",
                                          "end_of_speech_to_endpoint_ms", "end_of_speech_to_first_sound_ms")
                for column in applicable_columns:
                    if column == "cancellation_to_silence_ms":
                        applicable = selected
                        threshold, minimum = 250, CANCEL_MINIMUM
                    elif column == "endpoint_to_local_feedback_ms":
                        applicable, threshold, minimum = selected, 200, FIRST_SOUND_MINIMUM
                    else:
                        applicable, threshold, minimum = successful, 5000 if column in (
                            "endpoint_to_first_sound_ms", "endpoint_to_playback_start_ms"
                        ) else None, FIRST_SOUND_MINIMUM
                    durations[column] = metric(applicable, column, threshold, minimum=minimum)
                cohorts.append({
                    "evidence_type": evidence, "measurement_source": source, "kind": kind,
                    "field_evidence": evidence == "REAL" and source == "PHYSICAL_OBSERVATION",
                    **counts(selected), "timings": durations,
                })

    physical = [row for row in samples if row["evidence_type"] == "REAL"
                and row["measurement_source"] == "PHYSICAL_OBSERVATION"]
    ordinary = [row for row in physical if row["kind"] == "SHORT_CHAT"]
    successful = [row for row in ordinary if row["result"] == "SUCCESS"]
    first_sound = metric(successful, "endpoint_to_first_sound_ms", 5000, percentiles=True)
    first_sound["unresolved_attempts"] = sum(row["result"] == "MISSING" for row in ordinary)
    first_sound["p50_threshold_ms"] = 2500
    first_sound["p95_threshold_ms"] = 5000
    if first_sound["p50_ms"] is None:
        first_sound["status"] = "UNKNOWN"
    elif first_sound["p50_ms"] > 2500 or first_sound["p95_ms"] > 5000:
        first_sound["status"] = "FAIL"
    else:
        first_sound["status"] = "UNKNOWN" if (
            first_sound["missing_samples"] or first_sound["unresolved_attempts"]
        ) else "PASS"
    feedback = metric([row for row in physical if row["kind"] != "CANCEL"],
                      "endpoint_to_local_feedback_ms", 200)
    feedback["status"] = per_sample_gate(feedback)
    cancellation = metric([row for row in physical if row["kind"] == "CANCEL"],
                          "cancellation_to_silence_ms", 250, minimum=CANCEL_MINIMUM)
    cancellation["status"] = per_sample_gate(cancellation)
    cancellation["p95_ms"] = None  # Ten stops cannot establish a stable p95.
    gates = (first_sound["status"], feedback["status"], cancellation["status"])
    status = "FAIL" if "FAIL" in gates else "PASS" if all(gate == "PASS" for gate in gates) else "UNKNOWN"
    return {
        "schema_version": 1, "total_records": len(samples),
        "synthetic_records": sum(row["evidence_type"] == "SYNTHETIC" for row in samples),
        "percentile_method": "nearest-rank: sorted[ceil(p * N) - 1]",
        "scope": "Physical timing only; functional accuracy and installation are separate evidence.",
        "device_playback_start_definition": "First successful codec write; not acoustic first sound.",
        "device_local_feedback_definition": "Accepted local PROCESSING state; not completed LCD frame.",
        "cohorts": cohorts,
        "physical_timing_acceptance": {
            "status": status, "ordinary_short_chat": counts(ordinary),
            "endpoint_to_first_sound": first_sound,
            "endpoint_to_local_feedback": feedback,
            "cancellation_to_silence": cancellation,
            "end_of_speech_to_endpoint": metric(successful, "end_of_speech_to_endpoint_ms", percentiles=True),
            "end_of_speech_to_first_sound": metric(successful, "end_of_speech_to_first_sound_ms", percentiles=True),
        },
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", type=Path, help="anonymous CSV matching the checked-in template")
    parser.add_argument("--output", type=Path, help="write JSON to a separate file; default is stdout")
    args = parser.parse_args(argv)
    try:
        if args.output and args.output.resolve() == args.csv_path.resolve():
            raise InvalidEvidence("JSON output must differ from CSV input")
        report = analyze(read_samples(args.csv_path))
        encoded = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        if args.output:
            args.output.write_text(encoded, encoding="utf-8")
        else:
            sys.stdout.write(encoded)
    except (InvalidEvidence, OSError) as error:
        # File paths and untrusted CSV values are deliberately absent from errors.
        message = str(error) if isinstance(error, InvalidEvidence) else "cannot read input or write output"
        print(f"Invalid evidence: {message}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
