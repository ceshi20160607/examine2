"""Record this task's local token counters; never infer money from account quota."""
import json
import os
from pathlib import Path
from datetime import datetime, timezone

home = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
thread = os.environ["CODEX_THREAD_ID"]
session = next((home / "sessions").rglob(f"*{thread}*.jsonl"))
baseline = latest = started_at = None
for line in session.open(encoding="utf-8"):
    event = json.loads(line)
    payload = event.get("payload", {})
    if event.get("type") == "event_msg" and payload.get("type") == "token_count" and payload.get("info"):
        latest = {"timestamp": event["timestamp"], **payload["info"]["total_token_usage"]}
    if event.get("type") == "response_item" and payload.get("role") == "user":
        message = "\n".join(item.get("text", "") for item in payload.get("content", []))
        if message.strip().rstrip("，,") == "那你来继续做，统计消耗":
            baseline, started_at = latest, event["timestamp"]
if baseline is None or latest is None:
    raise SystemExit("Cannot establish the requested turn's token baseline; no estimate recorded.")
delta = {key: latest[key] - baseline[key] for key in latest if key != "timestamp"}
if any(value < 0 for value in delta.values()):
    raise SystemExit("Counters reset; cannot report a valid delta across this boundary.")
delta["uncached_input_tokens"] = delta["input_tokens"] - delta["cached_input_tokens"]
report = {"thread_id": thread, "started_at": started_at, "sampled_at": datetime.now(timezone.utc).isoformat(),
          "baseline": baseline, "latest": latest, "delta": delta, "cost": None,
          "notes": ["Input includes cached input; reasoning is a subset of output, not added twice.",
                    "Cumulative traffic is not context size or a currency bill. Final unsampled output is excluded."]}
target = Path(__file__).with_name("spark-usage-current.json")
target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"report": str(target), "through": latest["timestamp"], "delta": delta}, ensure_ascii=False))
