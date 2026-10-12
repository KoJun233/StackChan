"""Regression tests for anonymous timing evidence; every fixture is synthetic.

REAL labels below exercise acceptance rules, not real hardware measurements.
No generated fixture is retained as field evidence.
"""

import csv
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/analyze-voice-quality.py"
TEMPLATE = ROOT / "docs/project/baselines/voice-quality-samples-template.csv"
SPEC = importlib.util.spec_from_file_location("voice_quality_analysis", SCRIPT)
QUALITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(QUALITY)


def sample(index, **changes):
    row = dict.fromkeys(QUALITY.COLUMNS, "")
    row.update({
        "sample_id": f"S{index:03d}", "evidence_type": "REAL",
        "kind": "SHORT_CHAT", "measurement_source": "PHYSICAL_OBSERVATION",
        "condition": "QUIET_WAKE", "result": "SUCCESS",
        "endpoint_to_local_feedback_ms": 50, "endpoint_to_first_sound_ms": 1000,
        "end_of_speech_to_endpoint_ms": 800, "end_of_speech_to_first_sound_ms": 1800,
    })
    row.update(changes)
    return row


def cancellation(index, value, **changes):
    row = sample(index, kind="CANCEL", result="CANCELLED",
                 endpoint_to_local_feedback_ms="", endpoint_to_first_sound_ms="",
                 end_of_speech_to_endpoint_ms="", end_of_speech_to_first_sound_ms="",
                 cancellation_to_silence_ms=value)
    row.update(changes)
    return row


class VoiceQualityAnalysisTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="r01-anonymous-quality-")
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "samples.csv"

    def write(self, rows, columns=None):
        with self.path.open("w", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=columns or QUALITY.COLUMNS)
            writer.writeheader()
            writer.writerows(rows)
        return self.path

    def report(self, rows):
        return QUALITY.analyze(QUALITY.read_samples(self.write(rows)))

    def cli(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)],
                              capture_output=True, text=True, encoding="utf-8", check=False)

    def test_checked_in_template_is_empty_and_unknown(self):
        self.assertEqual(QUALITY.read_samples(TEMPLATE), [])
        result = self.cli(TEMPLATE)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report["total_records"], 0)
        self.assertEqual(report["cohorts"], [])
        gate = report["physical_timing_acceptance"]
        self.assertEqual(gate["status"], "UNKNOWN")
        self.assertIsNone(gate["endpoint_to_first_sound"]["p50_ms"])
        self.assertIsNone(gate["cancellation_to_silence"]["max_ms"])

    def test_twenty_and_twenty_nine_never_report_percentiles(self):
        for count in (20, 29):
            with self.subTest(count=count):
                gate = self.report([sample(i + 1) for i in range(count)])["physical_timing_acceptance"]
                self.assertEqual(gate["endpoint_to_first_sound"]["measured_samples"], count)
                self.assertIsNone(gate["endpoint_to_first_sound"]["p50_ms"])
                self.assertIsNone(gate["endpoint_to_first_sound"]["p95_ms"])
                self.assertEqual(gate["endpoint_to_first_sound"]["status"], "UNKNOWN")

    def test_thirty_uses_nearest_rank_instead_of_averaging_median(self):
        rows = [sample(i, endpoint_to_first_sound_ms=i * 100,
                       end_of_speech_to_first_sound_ms=800 + i * 100) for i in range(1, 31)]
        gate = self.report(rows)["physical_timing_acceptance"]
        self.assertEqual(gate["endpoint_to_first_sound"]["p50_ms"], 1500)
        self.assertEqual(gate["endpoint_to_first_sound"]["p95_ms"], 2900)
        self.assertEqual(gate["endpoint_to_first_sound"]["max_ms"], 3000)
        self.assertEqual(gate["end_of_speech_to_endpoint"]["p50_ms"], 800)
        self.assertEqual(gate["end_of_speech_to_first_sound"]["p95_ms"], 3700)

    def test_p50_and_p95_thresholds_are_checked_separately(self):
        all_slow = [sample(i, endpoint_to_first_sound_ms=2501,
                           end_of_speech_to_first_sound_ms=3301) for i in range(1, 31)]
        self.assertEqual(self.report(all_slow)["physical_timing_acceptance"]["endpoint_to_first_sound"]["status"], "FAIL")
        tail_slow = [sample(i) for i in range(1, 29)] + [
            sample(i, endpoint_to_first_sound_ms=5001, end_of_speech_to_first_sound_ms=5801)
            for i in range(29, 31)
        ]
        summary = self.report(tail_slow)["physical_timing_acceptance"]["endpoint_to_first_sound"]
        self.assertEqual(summary["p50_ms"], 1000)
        self.assertEqual(summary["p95_ms"], 5001)
        self.assertEqual(summary["status"], "FAIL")
        boundary = [sample(i, endpoint_to_first_sound_ms=2500,
                           end_of_speech_to_first_sound_ms=3300) for i in range(1, 30)]
        boundary.append(sample(30, endpoint_to_first_sound_ms=5000, end_of_speech_to_first_sound_ms=5800))
        self.assertEqual(self.report(boundary)["physical_timing_acceptance"]["endpoint_to_first_sound"]["status"], "PASS")

    def test_device_tool_and_synthetic_samples_do_not_fill_physical_cohort(self):
        rows = [sample(i) for i in range(1, 30)]
        rows += [sample(i, evidence_type="SYNTHETIC") for i in range(30, 60)]
        rows += [sample(i, kind="TOOL") for i in range(60, 90)]
        rows += [sample(i, measurement_source="DEVICE_MONOTONIC",
                        endpoint_to_playback_start_ms=1000, endpoint_to_first_sound_ms="",
                        end_of_speech_to_endpoint_ms="", end_of_speech_to_first_sound_ms="")
                 for i in range(90, 120)]
        report = self.report(rows)
        self.assertEqual(len(report["cohorts"]), 4)
        self.assertEqual(report["physical_timing_acceptance"]["ordinary_short_chat"]["total_attempts"], 29)
        self.assertIsNone(report["physical_timing_acceptance"]["endpoint_to_first_sound"]["p95_ms"])
        device = next(cohort for cohort in report["cohorts"] if cohort["measurement_source"] == "DEVICE_MONOTONIC")
        self.assertFalse(device["field_evidence"])
        self.assertIsNone(device["timings"]["endpoint_to_playback_start_ms"]["p95_ms"])

    def test_synthetic_failures_and_slow_samples_remain_reference_only(self):
        rows = [sample(i, evidence_type="SYNTHETIC", endpoint_to_first_sound_ms=10000,
                       end_of_speech_to_first_sound_ms=10800,
                       endpoint_to_local_feedback_ms=500) for i in range(1, 31)]
        rows.append(sample(31, evidence_type="SYNTHETIC", result="FAILED", error_code="OFFLINE"))
        rows += [cancellation(i, 400, evidence_type="SYNTHETIC") for i in range(32, 42)]
        report = self.report(rows)
        self.assertEqual(report["synthetic_records"], 41)
        self.assertEqual(report["physical_timing_acceptance"]["status"], "UNKNOWN")
        cohort = next(item for item in report["cohorts"] if item["kind"] == "SHORT_CHAT")
        self.assertEqual(cohort["failure_codes"], {"OFFLINE": 1})
        self.assertEqual(cohort["timings"]["endpoint_to_first_sound_ms"]["over_threshold_samples"], 30)
        self.assertIsNone(cohort["timings"]["endpoint_to_first_sound_ms"]["p95_ms"])

    def test_slowest_success_is_kept_even_when_it_does_not_change_p95(self):
        rows = [sample(i) for i in range(1, 30)]
        rows.append(sample(30, endpoint_to_first_sound_ms=20000, end_of_speech_to_first_sound_ms=20800))
        summary = self.report(rows)["physical_timing_acceptance"]["endpoint_to_first_sound"]
        self.assertEqual(summary["status"], "PASS")
        self.assertEqual(summary["p95_ms"], 1000)
        self.assertEqual(summary["max_ms"], 20000)
        self.assertEqual(summary["over_threshold_samples"], 1)
        self.assertEqual(summary["measurements"][-1], {"sample_id": "S030", "ms": 20000})

    def test_all_failures_timeouts_missing_and_cancelled_attempts_are_counted(self):
        rows = [sample(i) for i in range(1, 31)]
        for index, result, error in ((31, "FAILED", "ASR_UNAVAILABLE"),
                                    (32, "FAILED", ""), (33, "TIMEOUT", "TIMEOUT"),
                                    (34, "MISSING", ""), (35, "CANCELLED", "")):
            rows.append(sample(index, result=result, error_code=error,
                               endpoint_to_first_sound_ms="", end_of_speech_to_first_sound_ms=""))
        report = self.report(rows)
        attempts = report["physical_timing_acceptance"]["ordinary_short_chat"]
        self.assertEqual(attempts["total_attempts"], 35)
        self.assertEqual(attempts["results"], {"SUCCESS": 30, "FAILED": 2, "CANCELLED": 1, "TIMEOUT": 1, "MISSING": 1})
        self.assertEqual(attempts["failure_codes"], {"ASR_UNAVAILABLE": 1, "TIMEOUT": 1, "UNKNOWN": 1})
        self.assertEqual(report["physical_timing_acceptance"]["endpoint_to_first_sound"]["measured_samples"], 30)
        self.assertEqual(report["physical_timing_acceptance"]["endpoint_to_first_sound"]["unresolved_attempts"], 1)
        self.assertEqual(report["physical_timing_acceptance"]["endpoint_to_first_sound"]["status"], "UNKNOWN")
        self.assertEqual(report["physical_timing_acceptance"]["endpoint_to_local_feedback"]["measured_samples"], 35)

    def test_missing_successful_measurement_does_not_become_zero_or_pass(self):
        rows = [sample(i) for i in range(1, 31)]
        rows.append(sample(31, endpoint_to_first_sound_ms="", end_of_speech_to_first_sound_ms=""))
        summary = self.report(rows)["physical_timing_acceptance"]["endpoint_to_first_sound"]
        self.assertEqual(summary["measured_samples"], 30)
        self.assertEqual(summary["missing_samples"], 1)
        self.assertEqual(summary["status"], "UNKNOWN")
        self.assertEqual(summary["p50_ms"], 1000)

    def test_each_feedback_sample_is_checked_including_later_failure(self):
        report = self.report([sample(1, result="FAILED", error_code="TTS_UNAVAILABLE",
                                    endpoint_to_local_feedback_ms=201)])
        feedback = report["physical_timing_acceptance"]["endpoint_to_local_feedback"]
        self.assertEqual(feedback["status"], "FAIL")
        self.assertEqual(feedback["over_threshold_samples"], 1)
        self.assertEqual(report["physical_timing_acceptance"]["status"], "FAIL")

    def test_cancellation_requires_ten_samples_and_never_claims_stable_p95(self):
        for count, expected in ((9, "UNKNOWN"), (10, "PASS")):
            with self.subTest(count=count):
                summary = self.report([cancellation(i, 250) for i in range(1, count + 1)])["physical_timing_acceptance"]["cancellation_to_silence"]
                self.assertEqual(summary["status"], expected)
                self.assertEqual(summary["max_ms"], 250)
                self.assertEqual(len(summary["measurements"]), count)
                self.assertIsNone(summary["p50_ms"])
                self.assertIsNone(summary["p95_ms"])

    def test_one_slow_or_missing_stop_cannot_pass(self):
        rows = [cancellation(i, 100) for i in range(1, 11)]
        rows.append(cancellation(11, 251))
        self.assertEqual(self.report(rows)["physical_timing_acceptance"]["cancellation_to_silence"]["status"], "FAIL")
        rows[-1] = cancellation(11, "", result="MISSING")
        summary = self.report(rows)["physical_timing_acceptance"]["cancellation_to_silence"]
        self.assertEqual(summary["missing_samples"], 1)
        self.assertEqual(summary["status"], "UNKNOWN")

    def test_failed_and_timed_out_cancellation_values_match_cohort_and_gate(self):
        rows = [cancellation(1, 200),
                cancellation(2, 251, result="FAILED", error_code="PLAYBACK_FAILED"),
                cancellation(3, 400, result="TIMEOUT", error_code="TIMEOUT"),
                cancellation(4, "", result="MISSING")]
        report = self.report(rows)
        cohort = report["cohorts"][0]
        self.assertEqual(cohort["failure_codes"], {"PLAYBACK_FAILED": 1, "TIMEOUT": 1})
        timing = cohort["timings"]["cancellation_to_silence_ms"]
        gate = report["physical_timing_acceptance"]["cancellation_to_silence"]
        for summary in (timing, gate):
            self.assertEqual(summary["applicable_attempts"], 4)
            self.assertEqual(summary["measured_samples"], 3)
            self.assertEqual(summary["missing_samples"], 1)
            self.assertEqual(summary["max_ms"], 400)
            self.assertEqual(summary["over_threshold_samples"], 2)
            self.assertEqual(summary["measurements"], [
                {"sample_id": "S001", "ms": 200}, {"sample_id": "S002", "ms": 251},
                {"sample_id": "S003", "ms": 400},
            ])
        self.assertEqual(gate["status"], "FAIL")

    def test_all_missing_applicable_metrics_report_counts_without_mixing_sources(self):
        missing = dict.fromkeys(QUALITY.DURATION_COLUMNS, "")
        rows = [sample(1, measurement_source="DEVICE_MONOTONIC", **missing),
                sample(2, measurement_source="DEVICE_MONOTONIC", **missing),
                sample(3, measurement_source="DEVICE_MONOTONIC", result="FAILED",
                       error_code="OFFLINE", **missing),
                sample(4, **missing), sample(5, **missing),
                cancellation(6, "", result="MISSING")]
        report = self.report(rows)
        device = next(row for row in report["cohorts"] if row["measurement_source"] == "DEVICE_MONOTONIC")
        physical = next(row for row in report["cohorts"] if row["measurement_source"] == "PHYSICAL_OBSERVATION" and row["kind"] == "SHORT_CHAT")
        cancel = next(row for row in report["cohorts"] if row["kind"] == "CANCEL")
        self.assertEqual(set(device["timings"]), {"endpoint_to_local_feedback_ms", "endpoint_to_playback_start_ms"})
        self.assertEqual(device["timings"]["endpoint_to_local_feedback_ms"]["missing_samples"], 3)
        self.assertEqual(device["timings"]["endpoint_to_playback_start_ms"]["missing_samples"], 2)
        self.assertEqual(set(physical["timings"]), {"endpoint_to_local_feedback_ms", "endpoint_to_first_sound_ms",
                                                  "end_of_speech_to_endpoint_ms", "end_of_speech_to_first_sound_ms"})
        self.assertEqual(set(cancel["timings"]), {"cancellation_to_silence_ms"})
        self.assertEqual(cancel["timings"]["cancellation_to_silence_ms"]["missing_samples"], 1)
        for summary in (*device["timings"].values(), *physical["timings"].values(), *cancel["timings"].values()):
            self.assertEqual(summary["measured_samples"], 0)
            self.assertIsNone(summary["max_ms"])
            self.assertIsNone(summary["p50_ms"])
            self.assertIsNone(summary["p95_ms"])
        self.assertEqual(report["physical_timing_acceptance"]["status"], "UNKNOWN")

    def test_only_complete_real_physical_timings_can_pass_all_timing_gates(self):
        rows = [sample(i) for i in range(1, 31)] + [cancellation(i, 100) for i in range(31, 41)]
        self.assertEqual(self.report(rows)["physical_timing_acceptance"]["status"], "PASS")
        for row in rows:
            row["evidence_type"] = "SYNTHETIC"
        self.assertEqual(self.report(rows)["physical_timing_acceptance"]["status"], "UNKNOWN")

    def test_duplicate_and_identifying_ids_are_rejected_without_echoing_contents(self):
        for rows in ([sample(1), sample(1)], [sample(1, sample_id="S000")],
                     [sample(1, sample_id="S0001")], [sample(1, sample_id="private-canary-person@example.com")]):
            with self.subTest(rows=len(rows)):
                result = self.cli(self.write(rows))
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, "")
                self.assertNotIn("private-canary", result.stderr)

    def test_private_extra_duplicate_and_reordered_headers_are_rejected(self):
        for columns in (QUALITY.COLUMNS + ("transcript",), QUALITY.COLUMNS + ("sample_id",),
                        tuple(reversed(QUALITY.COLUMNS))):
            with self.subTest(columns=columns):
                self.write([], columns)
                with self.assertRaises(QUALITY.InvalidEvidence):
                    QUALITY.read_samples(self.path)

    def test_invalid_enums_and_free_text_are_rejected_without_echoing(self):
        for changes in ({"condition": "private-canary conversation text"},
                        {"error_code": "private-canary provider credentials"},
                        {"measurement_source": "SERVER_DURATION"},
                        {"evidence_type": "UNKNOWN"}, {"result": "COMPLETED"},
                        {"kind": "VOICE"}):
            with self.subTest(changes=tuple(changes)):
                result = self.cli(self.write([sample(1, **changes)]))
                self.assertEqual(result.returncode, 2)
                self.assertNotIn("private-canary", result.stderr)
                self.assertEqual(result.stdout, "")

    def test_duration_values_must_be_bounded_nonnegative_integers(self):
        for value in ("-1", "+1", "1.5", "NaN", "inf", "true", "300001", "1000000"):
            with self.subTest(value=value):
                with self.assertRaises(QUALITY.InvalidEvidence):
                    QUALITY.read_samples(self.write([sample(1, endpoint_to_local_feedback_ms=value)]))
        samples = QUALITY.read_samples(self.write([sample(1, endpoint_to_local_feedback_ms=0)]))
        self.assertEqual(samples[0]["endpoint_to_local_feedback_ms"], 0)

    def test_mixed_sources_and_inconsistent_physical_markers_are_rejected(self):
        for changes in ({"measurement_source": "DEVICE_MONOTONIC"},
                        {"endpoint_to_playback_start_ms": 10},
                        {"cancellation_to_silence_ms": 10},
                        {"end_of_speech_to_first_sound_ms": 900},
                        {"end_of_speech_to_first_sound_ms": 2000},
                        {"result": "SUCCESS", "error_code": "OFFLINE"}):
            with self.subTest(changes=tuple(changes)):
                with self.assertRaises(QUALITY.InvalidEvidence):
                    QUALITY.read_samples(self.write([sample(1, **changes)]))

    def test_output_file_round_trip_and_input_collision_protection(self):
        self.write([sample(1)])
        output = Path(self.directory.name) / "report.json"
        result = self.cli(self.path, "--output", output)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["total_records"], 1)
        before = self.path.read_bytes()
        result = self.cli(self.path, "--output", self.path)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(self.path.read_bytes(), before)

    def test_invalid_input_does_not_replace_existing_output(self):
        self.write([sample(1, condition="private-canary")])
        output = Path(self.directory.name) / "report.json"
        output.write_text("existing evidence", encoding="utf-8")
        result = self.cli(self.path, "--output", output)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(output.read_text(encoding="utf-8"), "existing evidence")
        self.assertNotIn("private-canary", result.stderr)

    def test_malformed_csv_and_bom_handling(self):
        self.write([sample(1)])
        content = self.path.read_text(encoding="utf-8")
        self.path.write_text("\ufeff" + content, encoding="utf-8")
        self.assertEqual(len(QUALITY.read_samples(self.path)), 1)
        self.path.write_text(",".join(QUALITY.COLUMNS) + '\n"unclosed\n', encoding="utf-8")
        with self.assertRaises(QUALITY.InvalidEvidence):
            QUALITY.read_samples(self.path)
        self.path.write_text(",".join(QUALITY.COLUMNS) + "\nS001,REAL\n", encoding="utf-8")
        with self.assertRaises(QUALITY.InvalidEvidence):
            QUALITY.read_samples(self.path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
