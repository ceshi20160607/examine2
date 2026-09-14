"""Record this task's local token counters; never infer money from account quota."""
import json
import os
import argparse
from pathlib import Path
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument("--latest-turn", action="store_true")
parser.add_argument("--latest-user-turn", action="store_true")
parser.add_argument("--output", default="spark-usage-current.json")
args = parser.parse_args()
home = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
thread = os.environ["CODEX_THREAD_ID"]
session = next((home / "sessions").rglob(f"*{thread}*.jsonl"))
baseline = latest = started_at = None
samples = []
for line in session.open(encoding="utf-8"):
    event = json.loads(line)
    payload = event.get("payload", {})
    if event.get("type") == "event_msg" and payload.get("type") == "token_count" and payload.get("info"):
        latest = {"timestamp": event["timestamp"], **payload["info"]["total_token_usage"]}
        if started_at is not None:
            samples.append(latest)
    if event.get("type") == "response_item" and payload.get("role") == "user":
        message = "\n".join(item.get("text", "") for item in payload.get("content", []))
        if args.latest_user_turn or (args.latest_turn and message.strip() == "继续") or (not args.latest_turn and message.strip().rstrip("，,") == "那你来继续做，统计消耗"):
            baseline, started_at = latest, event["timestamp"]
            samples = []
if baseline is None or latest is None:
    raise SystemExit("Cannot establish the requested turn's token baseline; no estimate recorded.")
counter_keys = [key for key in latest if key != "timestamp"]
delta = {key: 0 for key in counter_keys}
segment_base = baseline
resets = 0
for index, sample in enumerate(samples):
    if any(sample[key] < segment_base[key] for key in counter_keys):
        # A fresh counter starts with the first response after a model/session boundary.
        # Its value already includes that response, so the comparable base is zero.
        segment_base = {key: 0 for key in counter_keys}
        resets += 1
    if index == len(samples) - 1 or any(
        next_sample[key] < sample[key]
        for next_sample in samples[index + 1:index + 2]
        for key in counter_keys
    ):
        for key in counter_keys:
            delta[key] += sample[key] - segment_base[key]
        segment_base = sample
delta["uncached_input_tokens"] = delta["input_tokens"] - delta["cached_input_tokens"]
report = {"thread_id": thread, "started_at": started_at, "sampled_at": datetime.now(timezone.utc).isoformat(),
          "baseline": baseline, "latest": latest, "delta": delta, "cost": None,
          "notes": ["Input includes cached input; reasoning is a subset of output, not added twice.",
                    "Cumulative traffic is not context size or a currency bill. Final unsampled output is excluded.",
                    f"Counters reset {resets} time(s); deltas were summed across comparable counter segments."]}
target = Path(__file__).with_name(args.output)
target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"report": str(target), "through": latest["timestamp"], "delta": delta}, ensure_ascii=False))
