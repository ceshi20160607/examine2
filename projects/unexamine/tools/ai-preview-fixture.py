"""本地预览环境的 OpenAI 兼容模型夹具，仅用于验证 Agent 网络与权限链路。"""

import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        size = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(size))
        prompt = request["messages"][-1]["content"]
        if "用户输入：" in prompt:
            source = prompt.split("用户输入：", 1)[-1]
            title = re.search(r"标题[：:]\s*([^；;\n]+)", source)
            name = re.search(r"商机名称[：:]\s*([^；;\n]+)", source)
            content = "\n".join(part for part in (
                f"标题: {title.group(1).strip()}" if title else "",
                f"field_481782d69b: {name.group(1).strip()}" if name else "",
            ) if part)
        else:
            facts = json.loads(prompt)
            titles = "、".join(source["title"] for source in facts.get("sources", []))
            content = f"当前权限范围内有 {facts['total']} 条商机。{titles}"
        result = {"choices": [{"message": {"role": "assistant", "content": content}}],
                  "usage": {"prompt_tokens": 100, "completion_tokens": 30}}
        body = json.dumps(result, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 18081), Handler).serve_forever()
