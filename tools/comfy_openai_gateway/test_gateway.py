import unittest
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from gateway import (
    PromptCache,
    build_asset_url,
    build_planner_payload,
    compile_runtime_rules,
    prompt_cache_key,
    select_reasoning_effort,
    inject_workflow_prompt,
    load_config,
    supported_models,
)


class GatewayWorkflowTest(unittest.TestCase):
    def test_runtime_rules_remove_empty_table_and_duplicate_lines_without_mutating_source(self):
        source = "\n# Identity\nKeep black hair.\n\n| field | value |\n|---|---|\nKeep black hair.\nNever change the art style.\n"

        runtime = compile_runtime_rules(source)

        self.assertEqual(source, "\n# Identity\nKeep black hair.\n\n| field | value |\n|---|---|\nKeep black hair.\nNever change the art style.\n")
        self.assertNotIn("| field", runtime)
        self.assertEqual(1, runtime.count("Keep black hair."))
        self.assertIn("Never change the art style.", runtime)

    def test_simple_requests_use_medium_reasoning_and_complex_requests_use_high(self):
        self.assertEqual("medium", select_reasoning_effort("她在窗边喝茶"))
        self.assertEqual(
            "high",
            select_reasoning_effort("两个人在雨夜车站告别，女孩回头望向镜头，风吹起裙摆，电影感斜构图和霓虹倒影"),
        )

    def test_prompt_cache_key_changes_when_appearance_or_source_rules_change(self):
        first = prompt_cache_key("a request", "black hair", "Keep the style.")

        self.assertNotEqual(first, prompt_cache_key("a request", "brown hair", "Keep the style."))
        self.assertNotEqual(first, prompt_cache_key("a request", "black hair", "Use another style."))

    def test_prompt_cache_key_changes_when_gateway_instruction_version_changes(self):
        self.assertNotEqual(
            prompt_cache_key("a request", "black hair", "Keep the style.", "instruction-v1"),
            prompt_cache_key("a request", "black hair", "Keep the style.", "instruction-v2"),
        )

    def test_prompt_cache_persists_a_final_prompt(self):
        with TemporaryDirectory() as directory:
            cache_path = Path(directory) / "cache.json"
            cache = PromptCache(cache_path)
            cache.put("test-key", "final prompt")

            self.assertEqual("final prompt", PromptCache(cache_path).get("test-key"))

    def test_asset_url_uses_tunnel_https_and_forwarded_host(self):
        self.assertEqual(
            "https://example.ngrok-free.dev/v1/assets/test-id",
            build_asset_url(
                host="127.0.0.1:8189",
                asset_id="test-id",
                forwarded_proto="https",
                forwarded_host="example.ngrok-free.dev",
            ),
        )

    def test_asset_url_keeps_http_for_direct_lan_clients(self):
        self.assertEqual(
            "http://192.168.2.18:8189/v1/assets/test-id",
            build_asset_url(host="192.168.2.18:8189", asset_id="test-id"),
        )

    def test_exposes_only_the_selected_wai_illustrious_model(self):
        self.assertEqual(
            {"wai-illustrious-v1"},
            {model["id"] for model in supported_models()},
        )

    def test_injects_only_the_existing_text_node_without_changing_the_save_node(self):
        template = {
            "48": {"inputs": {"text": ""}, "class_type": "JjkText"},
            "160": {"inputs": {"filename_prefix": "ComfyUI", "images": ["13", 0]}, "class_type": "SaveImage"},
        }

        workflow = inject_workflow_prompt(template, "48", "text", "a calm modern manhua portrait")

        self.assertEqual("a calm modern manhua portrait", workflow["48"]["inputs"]["text"])
        self.assertEqual(template["160"], workflow["160"])
        self.assertEqual("", template["48"]["inputs"]["text"])

    def test_loads_a_windows_utf8_bom_config_file(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            workflow = root / "workflow.json"
            rules = root / "rules.txt"
            config = root / "gateway.json"
            workflow.write_text("{}", encoding="utf-8")
            rules.write_text("Keep the request exact.", encoding="utf-8")
            config.write_text(
                json.dumps(
                    {
                        "api_key": "local-key",
                        "comfy_url": "http://127.0.0.1:8188",
                        "workflow_file": str(workflow),
                        "prompt_node_id": "48",
                        "prompt_input": "text",
                        "output_node_id": "160",
                        "deepseek_prompt_planner": {
                            "base_url": "https://api.deepseek.com/v1",
                            "api_key": "deepseek-key",
                            "model": "deepseek-chat",
                            "rules_file": str(rules),
                            "character_appearance": "black hair",
                        },
                    }
                ),
                encoding="utf-8-sig",
            )

            loaded = load_config(config)

            self.assertEqual("local-key", loaded.api_key)
            self.assertEqual("48", loaded.prompt_node_id)

    def test_deepseek_payload_uses_one_shared_rule_file_and_preserves_the_request(self):
        payload = build_planner_payload(
            model="deepseek-chat",
            request="她穿着蓝色连衣裙站在雨后的车站",
            shared_rules="Use Chinese modern manhua illustration and preserve the requested clothing.",
            character_appearance="林见夏，黑色高马尾，杏眼，纤细身材，清冷警惕气质。",
        )

        self.assertEqual("deepseek-chat", payload["model"])
        self.assertIn("蓝色连衣裙", payload["messages"][1]["content"])
        self.assertIn("preserve the requested clothing", payload["messages"][0]["content"])
        self.assertIn("黑色高马尾", payload["messages"][0]["content"])

    def test_gateway_instruction_requires_detailed_private_visual_planning_without_rewriting_owner_rules(self):
        owner_rules = "Keep the requested art style exactly."
        payload = build_planner_payload(
            model="deepseek-v4-pro",
            request="她靠在窗边",
            shared_rules=owner_rules,
            character_appearance="black hair",
        )

        instruction = payload["messages"][0]["content"]
        self.assertIn("three silent passes", instruction)
        self.assertIn("owner rules verbatim", instruction)
        self.assertIn("identity, clothing, action, expression, camera, environment", instruction)
        self.assertIn("exactly one pair of arms", instruction)
        self.assertIn("five fingers on each visible hand", instruction)
        self.assertIn("extra limbs", instruction)
        self.assertIn(owner_rules, instruction)


if __name__ == "__main__":
    unittest.main()
