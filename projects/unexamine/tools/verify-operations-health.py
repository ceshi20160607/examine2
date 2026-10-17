"""验证系统后台体检的真实依赖、权限、Redis 故障和重启读回。"""

import importlib.util
import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def token(username, system_id):
    platform = fixture.expected("/api/auth/login", "POST", {
        "username": username, "password": "correct-password"})
    return fixture.expected(f"/api/systems/{system_id}/enter", "POST", {},
                            platform["accessToken"])["tokens"]["accessToken"]


def health(result, redis_status):
    checks = {item["code"]: item for item in result["items"]}
    if set(checks) != {"DATABASE", "REDIS", "FILE_STORAGE", "APPLICATION_SECRET",
                       "DATABASE_MIGRATION"} or checks["REDIS"]["status"] != redis_status:
        raise AssertionError(f"系统体检依赖项不全或 Redis 状态错误：{checks}")
    if not result["releaseVersion"] or result["migrationVersion"] in ("", "unknown"):
        raise AssertionError("体检没有服务版本或数据库脚本版本")
    if result["jobs"] is None or result["recentErrors"] is None or not result["requestId"]:
        raise AssertionError("体检没有后台作业、最近错误或 requestId")
    if any("secret" in json.dumps(item["metric"], ensure_ascii=False).lower()
           for item in result["items"] if item["code"] == "APPLICATION_SECRET"):
        raise AssertionError("密钥检查指标中不应出现明文")
    return checks


def main():
    system_id = int(sys.argv[1]) if len(sys.argv) > 1 else 151
    owner_name = sys.argv[2] if len(sys.argv) > 2 else "g8_owner_75ca9359"
    viewer_name = sys.argv[3] if len(sys.argv) > 3 else "g8_viewer_75ca9359"
    owner = token(owner_name, system_id)
    viewer = token(viewer_name, system_id)
    for path, method in (("/health", "GET"), ("/health/recheck", "POST")):
        status, error = fixture.request("/api/admin/system/operations" + path, method,
                                        {} if method == "POST" else None, viewer)
        if status != 403:
            raise AssertionError(f"普通成员取得体检管理入口：{path}: {status}/{error}")
    normal = fixture.expected("/api/admin/system/operations/health/recheck", "POST", {}, owner)
    health(normal, "PASSED")
    if not normal["ready"] or normal["status"] != "READY":
        raise AssertionError("正常依赖没有通过体检")
    observed = fixture.expected("/api/admin/system/operations/observability?requestId="
                                + normal["requestId"], token=owner)
    if not observed["observable"] or observed["requestId"] != normal["requestId"]:
        raise AssertionError("体检 requestId 没有链路观测")

    paused = False
    try:
        subprocess.run(["docker", "pause", "unexamine-redis-1"], check=True,
                       capture_output=True, timeout=15)
        paused = True
        failure_status, failure = fixture.request(
            "/api/admin/system/operations/health/recheck", "POST", {}, owner)
    finally:
        if paused:
            subprocess.run(["docker", "unpause", "unexamine-redis-1"], check=True,
                           capture_output=True, timeout=15)
    if failure_status != 200 or failure.get("code") not in ("OK", "SUCCESS", None):
        raise AssertionError(f"Redis 暂停时体检未形成可读的失败结果：{failure_status}/{failure}")
    failed = failure["data"]
    checks = health(failed, "FAILED")
    if failed["ready"] or failed["status"] != "NOT_READY" or not checks["REDIS"]["message"]:
        raise AssertionError("Redis 不可用未形成失败项及原因")
    if "--leave-failed" in sys.argv:
        print(json.dumps({"systemId": system_id, "normalRun": normal["id"],
                          "failedRun": failed["id"], "redisMessage": checks["REDIS"]["message"],
                          "requestId": failed["requestId"]}, ensure_ascii=False))
        return
    recovered = fixture.expected("/api/admin/system/operations/health/recheck", "POST", {}, owner)
    health(recovered, "PASSED")
    if not recovered["ready"]:
        raise AssertionError("Redis 恢复后体检仍未就绪")
    latest = fixture.expected("/api/admin/system/operations/health", token=owner)
    if latest["id"] != recovered["id"]:
        raise AssertionError("体检历史未持久化读回")
    if "--restart" in sys.argv:
        subprocess.run(["docker", "restart", "unexamine-g1-preview"], check=True,
                       capture_output=True, timeout=40)
        for _ in range(70):
            try:
                with urllib.request.urlopen(fixture.BASE + "/actuator/health", timeout=2) as response:
                    if json.loads(response.read()).get("status") == "UP":
                        break
            except OSError:
                pass
            time.sleep(0.5)
        else:
            raise AssertionError("体检服务重启后未恢复")
        latest = fixture.expected("/api/admin/system/operations/health", token=owner)
        if latest["id"] != recovered["id"] or not latest["ready"]:
            raise AssertionError("重启后没有读回最近体检批次")
    print(json.dumps({"systemId": system_id, "normalRun": normal["id"],
                      "failedRun": failed["id"], "recoveredRun": recovered["id"],
                      "migrationVersion": recovered["migrationVersion"],
                      "releaseVersion": recovered["releaseVersion"],
                      "recentErrors": len(recovered["recentErrors"]),
                      "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
