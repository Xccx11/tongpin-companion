"""OpenAI-compatible image endpoint backed by a local ComfyUI instance."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import secrets
import threading
import time
import uuid
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen


ASSET_TTL_SECONDS = 24 * 60 * 60
PROMPT_CACHE_LIMIT = 128
PLANNER_INSTRUCTION_VERSION = "detailed-visual-planning-v3-anatomy"


@dataclass(frozen=True)
class GatewayConfig:
    api_key: str
    comfy_url: str
    workflow_file: Path
    prompt_node_id: str
    prompt_input: str
    output_node_id: str
    host: str = "0.0.0.0"
    port: int = 8189
    planner: "PlannerConfig | None" = None


@dataclass(frozen=True)
class PlannerConfig:
    base_url: str
    api_key: str
    model: str
    rules_file: Path
    character_appearance: str
    runtime_rules_file: Path
    cache_file: Path


def supported_models() -> list[dict[str, str]]:
    return [{"id": "wai-illustrious-v1", "checkpoint": "waiNSFWIllustrious_v140.safetensors"}]


def compile_runtime_rules(source: str) -> str:
    """Remove presentation-only Markdown while preserving actual rule statements."""
    lines: list[str] = []
    seen: set[str] = set()
    for raw_line in source.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("|") or re.fullmatch(r"[-_:| ]+", line):
            continue
        line = re.sub(r"^(?:#{1,6}|[-*+])\s+", "", line).strip()
        if not line:
            continue
        identity = re.sub(r"\s+", " ", line).casefold()
        if identity in seen:
            continue
        seen.add(identity)
        lines.append(line)
    return "\n".join(lines)


def select_reasoning_effort(request: str) -> str:
    text = request.casefold()
    categories = (
        ("multi_subject", ("两人", "两个", "多人", "群像", "双人", "multiple people", "two people", "group")),
        ("composition", ("构图", "视角", "镜头", "俯视", "仰视", "斜构图", "composition", "camera angle", "close-up", "wide shot")),
        ("environment", ("场景", "室内", "室外", "车站", "街道", "房间", "雨夜", "夜晚", "背景", "setting", "environment")),
        ("action", ("动作", "回头", "奔跑", "拥抱", "抬手", "走路", "互动", "pose", "action", "movement")),
        ("style", ("画风", "风格", "光线", "色调", "style", "lighting", "palette")),
    )
    matched = sum(any(marker in text for marker in markers) for _, markers in categories)
    return "high" if matched >= 3 or len(text) >= 220 else "medium"


def prompt_cache_key(
    request: str,
    character_appearance: str,
    source_rules: str,
    instruction_version: str = PLANNER_INSTRUCTION_VERSION,
) -> str:
    payload = "\x1f".join((instruction_version, request.strip(), character_appearance.strip(), source_rules))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


class PromptCache:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._lock = threading.Lock()
        self._entries = self._load()

    def get(self, key: str) -> str | None:
        with self._lock:
            value = self._entries.get(key, {}).get("prompt")
            return value if isinstance(value, str) and value else None

    def put(self, key: str, prompt: str) -> None:
        with self._lock:
            self._entries[key] = {"prompt": prompt, "created_at": int(time.time())}
            if len(self._entries) > PROMPT_CACHE_LIMIT:
                oldest = sorted(self._entries, key=lambda item: int(self._entries[item].get("created_at", 0)))
                for stale_key in oldest[: len(self._entries) - PROMPT_CACHE_LIMIT]:
                    del self._entries[stale_key]
            self._persist()

    def _load(self) -> dict[str, dict[str, Any]]:
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
        except (FileNotFoundError, json.JSONDecodeError, OSError):
            return {}
        return value if isinstance(value, dict) else {}

    def _persist(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        temporary.write_text(json.dumps(self._entries, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        temporary.replace(self.path)


def build_planner_payload(
    model: str,
    request: str,
    shared_rules: str,
    character_appearance: str,
    reasoning_effort: str = "medium",
) -> dict[str, Any]:
    return {
        "model": model,
        "stream": False,
        "thinking": {"type": "enabled"},
        "reasoning_effort": reasoning_effort,
        "messages": [
            {
                "role": "system",
                "content": """You are a precise SDXL / Illustrious prompt expansion engine. Translate the user's exact image request into one detailed English positive ComfyUI prompt.

Instruction priority: explicit current request first, owner rules verbatim second, stable character appearance third. Never rewrite, summarize, weaken, override, or reinterpret owner rules. They are supplied below as the owner's source of truth. Do not make creative decisions: never substitute, add, remove, soften, or improve an explicit subject, clothing item, action, emotion, prop, setting, composition, or art style.

Before responding, perform three silent passes. First make a requirements ledger of every explicit visual fact and every forbidden change. Second turn only the requested facts into a single frozen visual moment, expanding each applicable fact into physically visible, renderable detail. Third check that identity, clothing, action, expression, camera, environment, anatomy, and style are mutually consistent. Do not reveal those passes.

The final prompt must use a clear visual order: subject count and identity, stable appearance, requested clothing and material/state, requested pose and action, expression and gaze, camera and framing, scene depth and concrete environment, then requested rendering style. For every applicable requested element, state concrete visible properties: facial micro-expression, hair behavior, fabric and folds, body weight and hand placement, eye line, spatial relationship, foreground/midground/background, palette, and rendering technique. Use detail to render the user's request faithfully, never to invent an unrequested story, outfit, prop, setting, or action.

Subject count and anatomy are hard constraints. Use exactly the requested number of people; never create incidental extra people in reflections, backgrounds, or duplicate bodies. For each human character, require one head, exactly one pair of arms, exactly two hands, and five fingers on each visible hand. The final positive prompt must include an anatomy anchor for every human scene: 'anatomically correct human body, exactly one pair of arms, two hands, five fingers on each visible hand'. During the silent final check, reject any wording that could imply extra limbs, duplicate hands, fused fingers, missing fingers, malformed hands, or a mismatched number of people. Use one coherent instant rather than a list of simultaneous poses. Avoid repetitive, vague filler such as 'beautiful', 'high quality', or generic cinematic claims unless the owner rules explicitly require it.

Return only one final English positive prompt. Do not include dialogue, captions, watermarks, UI, a negative prompt, markdown, explanations, or planning notes.

The following character appearance is a stable identity source of truth. Preserve it in every image unless the current request explicitly changes a visual detail:\n\n""" + character_appearance.strip() + """

The following are the owner rules verbatim. They are mandatory and apply to every request:\n\n""" + shared_rules.strip(),
            },
            {"role": "user", "content": request},
        ],
    }


def inject_workflow_prompt(
    template: dict[str, Any],
    prompt_node_id: str,
    prompt_input: str,
    prompt: str,
) -> dict[str, Any]:
    workflow = json.loads(json.dumps(template))
    try:
        workflow[prompt_node_id]["inputs"][prompt_input] = prompt
    except KeyError as error:
        raise ValueError(f"Workflow prompt node {prompt_node_id}.{prompt_input} was not found") from error
    return workflow


def build_asset_url(host: str, asset_id: str, forwarded_proto: str = "", forwarded_host: str = "") -> str:
    """Build a client-reachable URL when the gateway sits behind an HTTPS tunnel."""
    protocol = forwarded_proto.split(",", 1)[0].strip().lower()
    scheme = "https" if protocol == "https" else "http"
    public_host = forwarded_host.split(",", 1)[0].strip() or host
    return f"{scheme}://{public_host}/v1/assets/{asset_id}"


class ComfyClient:
    def __init__(self, comfy_url: str) -> None:
        self.root = comfy_url.rstrip("/")

    def generate(self, workflow: dict[str, Any], output_node_id: str) -> bytes:
        payload = json.dumps({"client_id": str(uuid.uuid4()), "prompt": workflow}).encode("utf-8")
        response = self._request("/prompt", data=payload, content_type="application/json")
        prompt_id = json.loads(response).get("prompt_id")
        if not prompt_id:
            raise RuntimeError("ComfyUI did not return a prompt id")
        image = self._wait_for_image(str(prompt_id), output_node_id)
        view_path = "/view?filename={}&subfolder={}&type={}".format(
            quote(image["filename"]),
            quote(image.get("subfolder", "")),
            quote(image.get("type", "output")),
        )
        return self._request(view_path)

    def _wait_for_image(self, prompt_id: str, output_node_id: str) -> dict[str, str]:
        deadline = time.monotonic() + 10 * 60
        while time.monotonic() < deadline:
            time.sleep(1.5)
            history = json.loads(self._request(f"/history/{quote(prompt_id)}"))
            entry = history.get(prompt_id)
            if not entry:
                continue
            status = str(entry.get("status", {}).get("status_str", "")).lower()
            if status in {"error", "failed", "cancelled", "canceled"}:
                raise RuntimeError("ComfyUI image task failed")
            images = entry.get("outputs", {}).get(output_node_id, {}).get("images", [])
            if images:
                return images[0]
        raise TimeoutError("ComfyUI image generation timed out")

    def _request(self, path: str, data: bytes | None = None, content_type: str | None = None) -> bytes:
        request = Request(self.root + path, data=data, method="POST" if data is not None else "GET")
        if content_type:
            request.add_header("Content-Type", content_type)
        try:
            with urlopen(request, timeout=30) as response:
                return response.read()
        except HTTPError as error:
            raise RuntimeError(f"ComfyUI HTTP {error.code}") from error
        except URLError as error:
            raise RuntimeError(f"Unable to reach ComfyUI: {error.reason}") from error


class OpenAiPromptPlanner:
    def __init__(self, config: PlannerConfig) -> None:
        self.config = config
        self.cache = PromptCache(config.cache_file)

    def refine(self, request: str) -> str:
        source_rules = self.config.rules_file.read_text(encoding="utf-8").strip()
        if not source_rules:
            raise RuntimeError("The shared DeepSeek prompt rules file is empty")
        rules = compile_runtime_rules(source_rules)
        if not rules:
            raise RuntimeError("The shared DeepSeek prompt rules file has no usable rules")
        self._write_runtime_rules(rules)
        cache_key = prompt_cache_key(
            request,
            self.config.character_appearance,
            source_rules,
            PLANNER_INSTRUCTION_VERSION,
        )
        cached = self.cache.get(cache_key)
        if cached:
            return cached
        payload = json.dumps(
            build_planner_payload(
                self.config.model,
                request,
                rules,
                self.config.character_appearance,
                select_reasoning_effort(request),
            ),
            ensure_ascii=False,
        ).encode("utf-8")
        endpoint = self.config.base_url.rstrip("/") + "/chat/completions"
        http_request = Request(endpoint, data=payload, method="POST")
        http_request.add_header("Content-Type", "application/json")
        http_request.add_header("Authorization", f"Bearer {self.config.api_key}")
        try:
            with urlopen(http_request, timeout=90) as response:
                body = json.loads(response.read())
        except HTTPError as error:
            raw_detail = error.read().decode("utf-8", errors="replace")
            try:
                detail = str(json.loads(raw_detail).get("error", {}).get("message", "")).strip()
            except json.JSONDecodeError:
                detail = raw_detail.strip()
            if len(detail) > 600:
                detail = detail[:600] + "..."
            suffix = f": {detail}" if detail else ""
            raise RuntimeError(f"DeepSeek HTTP {error.code}{suffix}") from error
        except URLError as error:
            raise RuntimeError(f"Unable to reach DeepSeek: {error.reason}") from error
        prompt = str(body.get("choices", [{}])[0].get("message", {}).get("content", "")).strip()
        if not prompt:
            raise RuntimeError("DeepSeek returned an empty image prompt")
        self.cache.put(cache_key, prompt)
        return prompt

    def _write_runtime_rules(self, rules: str) -> None:
        target = self.config.runtime_rules_file
        try:
            if target.read_text(encoding="utf-8") == rules:
                return
        except FileNotFoundError:
            pass
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_text(rules, encoding="utf-8")
        temporary.replace(target)


class GatewayHandler(BaseHTTPRequestHandler):
    server: "GatewayServer"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._json(HTTPStatus.OK, {"status": "ok"})
            return
        # Image URLs are consumed by generic image loaders, which do not repeat
        # the caller's Bearer token. The random UUID is the temporary capability.
        if self.path.startswith("/v1/assets/"):
            self._serve_asset(self.path.rsplit("/", 1)[-1])
            return
        if not self._authorized():
            return
        if self.path == "/v1/models":
            self._json(HTTPStatus.OK, {"object": "list", "data": [{"id": item["id"], "object": "model", "owned_by": "local-comfyui"} for item in supported_models()]})
            return
        self._error(HTTPStatus.NOT_FOUND, "not_found", "Unknown endpoint")

    def do_POST(self) -> None:  # noqa: N802
        if not self._authorized():
            return
        if self.path != "/v1/images/generations":
            self._error(HTTPStatus.NOT_FOUND, "not_found", "Unknown endpoint")
            return
        try:
            body = self._read_json()
            model = str(body.get("model", "wai-illustrious-v1"))
            requested_prompt = str(body.get("prompt", "")).strip()
            if not requested_prompt:
                raise ValueError("prompt is required")
            if model not in {item["id"] for item in supported_models()}:
                raise ValueError(f"Unsupported local image model: {model}")
            count = min(max(int(body.get("n", 1)), 1), 4)
            response_format = str(body.get("response_format", "url"))
            if response_format not in {"url", "b64_json"}:
                raise ValueError("response_format must be url or b64_json")
            prompt = self.server.planner.refine(requested_prompt) if self.server.planner else requested_prompt
            self.server.log_prompt(model, requested_prompt, prompt)
            data = []
            for _ in range(count):
                workflow = self.server.workflow_for(prompt)
                image = self.server.comfy.generate(workflow, self.server.config.output_node_id)
                asset_id = self.server.store_asset(image)
                if response_format == "b64_json":
                    data.append({"b64_json": base64.b64encode(image).decode("ascii")})
                else:
                    data.append({"url": self._asset_url(asset_id)})
            self._json(HTTPStatus.OK, {"created": int(time.time()), "data": data})
        except (ValueError, TypeError) as error:
            self._error(HTTPStatus.BAD_REQUEST, "invalid_request_error", str(error))
        except (RuntimeError, TimeoutError) as error:
            self._error(HTTPStatus.BAD_GATEWAY, "api_error", str(error))

    def _read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > 64 * 1024:
            raise ValueError("request body must be JSON under 64 KB")
        value = json.loads(self.rfile.read(length).decode("utf-8"))
        if not isinstance(value, dict):
            raise ValueError("request body must be a JSON object")
        return value

    def _authorized(self) -> bool:
        expected = f"Bearer {self.server.config.api_key}"
        if secrets.compare_digest(self.headers.get("Authorization", ""), expected):
            return True
        self._error(HTTPStatus.UNAUTHORIZED, "invalid_api_key", "Incorrect API key")
        return False

    def _serve_asset(self, asset_id: str) -> None:
        if not re.fullmatch(r"[0-9a-f-]{36}", asset_id):
            self._error(HTTPStatus.NOT_FOUND, "not_found", "Image not found")
            return
        path = self.server.asset_directory / f"{asset_id}.png"
        if not path.is_file():
            self._error(HTTPStatus.NOT_FOUND, "not_found", "Image not found")
            return
        payload = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _asset_url(self, asset_id: str) -> str:
        host = self.headers.get("Host", f"127.0.0.1:{self.server.server_port}")
        return build_asset_url(
            host=host,
            asset_id=asset_id,
            forwarded_proto=self.headers.get("X-Forwarded-Proto", ""),
            forwarded_host=self.headers.get("X-Forwarded-Host", ""),
        )

    def _json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _error(self, status: HTTPStatus, error_type: str, message: str) -> None:
        self._json(status, {"error": {"message": message, "type": error_type}})

    def log_message(self, format: str, *args: Any) -> None:
        return


class GatewayServer(ThreadingHTTPServer):
    def __init__(self, config: GatewayConfig, asset_directory: Path) -> None:
        super().__init__((config.host, config.port), GatewayHandler)
        self.config = config
        self.comfy = ComfyClient(config.comfy_url)
        self.planner = OpenAiPromptPlanner(config.planner) if config.planner else None
        self.workflow_template = json.loads(config.workflow_file.read_text(encoding="utf-8"))
        if config.output_node_id not in self.workflow_template:
            raise ValueError(f"Workflow output node {config.output_node_id} was not found")
        self.asset_directory = asset_directory
        self.asset_directory.mkdir(parents=True, exist_ok=True)
        self.prompt_log = self.asset_directory.parent / "gateway-prompts.jsonl"
        self._purge_expired_assets()

    def store_asset(self, payload: bytes) -> str:
        asset_id = str(uuid.uuid4())
        (self.asset_directory / f"{asset_id}.png").write_bytes(payload)
        return asset_id

    def workflow_for(self, prompt: str) -> dict[str, Any]:
        return inject_workflow_prompt(
            self.workflow_template,
            self.config.prompt_node_id,
            self.config.prompt_input,
            prompt,
        )

    def _purge_expired_assets(self) -> None:
        threshold = time.time() - ASSET_TTL_SECONDS
        for path in self.asset_directory.glob("*.png"):
            if path.stat().st_mtime < threshold:
                path.unlink(missing_ok=True)

    def log_prompt(self, model: str, request: str, final_prompt: str) -> None:
        entry = {
            "created_at": int(time.time()),
            "model": model,
            "requested_prompt": request,
            "comfy_prompt": final_prompt,
        }
        with self.prompt_log.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False) + "\n")


def load_config(path: Path) -> GatewayConfig:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    api_key = str(value.get("api_key", "")).strip()
    comfy_url = str(value.get("comfy_url", "")).strip()
    workflow_file = Path(str(value.get("workflow_file", "")).strip())
    prompt_node_id = str(value.get("prompt_node_id", "")).strip()
    prompt_input = str(value.get("prompt_input", "")).strip()
    output_node_id = str(value.get("output_node_id", "")).strip()
    if not api_key or not comfy_url or not workflow_file.is_file() or not prompt_node_id or not prompt_input or not output_node_id:
        raise ValueError("gateway config needs api_key, comfy_url, workflow_file, prompt_node_id, prompt_input, and output_node_id")
    planner_value = value.get("deepseek_prompt_planner")
    planner = None
    if planner_value is not None:
        if not isinstance(planner_value, dict):
            raise ValueError("deepseek_prompt_planner must be an object")
        base_url = str(planner_value.get("base_url", "")).strip()
        planner_key = str(planner_value.get("api_key", "")).strip()
        model = str(planner_value.get("model", "")).strip()
        rules_file = Path(str(planner_value.get("rules_file", "")).strip())
        character_appearance = str(planner_value.get("character_appearance", "")).strip()
        if not base_url or not planner_key or not model or not rules_file.is_file() or not character_appearance:
            raise ValueError("deepseek_prompt_planner needs base_url, api_key, model, rules_file, and character_appearance")
        planner = PlannerConfig(
            base_url=base_url,
            api_key=planner_key,
            model=model,
            rules_file=rules_file,
            character_appearance=character_appearance,
            runtime_rules_file=Path(str(planner_value.get("runtime_rules_file", rules_file.with_name(rules_file.stem + ".runtime" + rules_file.suffix))).strip()),
            cache_file=Path(str(planner_value.get("cache_file", rules_file.with_name("comfy-openai-prompt-cache.json"))).strip()),
        )
    return GatewayConfig(
        api_key=api_key,
        comfy_url=comfy_url,
        workflow_file=workflow_file,
        prompt_node_id=prompt_node_id,
        prompt_input=prompt_input,
        output_node_id=output_node_id,
        host=str(value.get("host", "0.0.0.0")),
        port=int(value.get("port", 8189)),
        planner=planner,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Local ComfyUI OpenAI image gateway")
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--assets", required=True, type=Path)
    args = parser.parse_args()
    config = load_config(args.config)
    server = GatewayServer(config, args.assets)
    print(f"Comfy OpenAI gateway listening on http://{config.host}:{config.port}/v1", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
