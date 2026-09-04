#!/usr/bin/env python3
"""One-shot, file-editor-only OpenHands worker for the Bug Fixer service."""

import argparse
import os
import sys
from pathlib import Path


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run an isolated OpenHands code-fix task.")
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--prompt", required=True)
    return parser.parse_args()


def main() -> int:
    args = arguments()
    workspace = Path(args.workspace).resolve()
    if not workspace.is_dir():
        raise ValueError(f"Workspace does not exist: {workspace}")

    provider = os.environ["OPENHANDS_PROVIDER"].upper()
    model = os.environ["OPENHANDS_MODEL"]
    api_key = os.environ["OPENHANDS_API_KEY"]
    base_url = os.environ["OPENHANDS_BASE_URL"]
    if provider not in {"GEMINI", "GROQ"}:
        raise ValueError("OPENHANDS_PROVIDER must be GEMINI or GROQ")

    # LiteLLM initializes a tokenizer while importing OpenHands. Persist the
    # cache inside the deployment-owned runtime directory, never the source repo.
    cache_dir = Path(os.environ.get("OPENHANDS_TIKTOKEN_CACHE_DIR", Path(__file__).parent / ".tiktoken-cache"))
    cache_dir.mkdir(parents=True, exist_ok=True)
    # LiteLLM uses CUSTOM_TIKTOKEN_CACHE_DIR and then maps it to
    # TIKTOKEN_CACHE_DIR during import.
    os.environ["CUSTOM_TIKTOKEN_CACHE_DIR"] = str(cache_dir)
    os.environ["TIKTOKEN_CACHE_DIR"] = str(cache_dir)

    from openhands.sdk import Agent, Conversation, LLM, Tool
    from openhands.sdk.context.agent_context import AgentContext
    from openhands.sdk.conversation.response_utils import get_agent_final_response
    # This import registers the only environment-changing tool made available
    # to the agent. The SDK intentionally keeps it in the separate
    # ``openhands-tools`` distribution.
    from openhands.tools.file_editor import FileEditorTool
    from pydantic import SecretStr

    model_name = f"openai/{model}" if provider == "GEMINI" else f"groq/{model}"
    llm = LLM(
        usage_id="bug-fixer-agent",
        model=model_name,
        base_url=base_url,
        api_key=SecretStr(api_key),
        timeout=120,
        num_retries=2,
    )

    # No TerminalTool, browser, web, or custom network tool is registered.
    # Compilation and tests remain a separate fixed Java validation stage.
    agent = Agent(
        llm=llm,
        tools=[Tool(name=FileEditorTool.name)],
        # OpenHands' stock prompt suggests persisting learnings in AGENTS.md.
        # This worker's workspace is a one-shot, disposable PR workspace, so
        # persistent memory and all skill sources are deliberately disabled.
        agent_context=AgentContext(
            load_memory=False,
            load_project_skills=False,
            load_public_skills=False,
            load_user_skills=False,
            system_message_suffix="""
This is an ephemeral, single-issue bug-fix job. Do not create, modify, or use
persistent memory or agent instruction artifacts, including AGENTS.md,
MEMORY.md, .openhands/, notes, changelogs, or change-summary documents. Ignore
any general guidance to record learnings: the Git commit and pull request are
the audit trail. Only modify source code and a directly relevant existing or
new automated test when needed for the stated issue.
""",
        ),
    )

    def on_event(event) -> None:
        print(f"OPENHANDS_EVENT type={type(event).__name__}", flush=True)

    conversation = Conversation(
        agent=agent,
        workspace=workspace,
        # The container mounts the repository at /workspace, whose parent is
        # the read-only filesystem root. Keep ephemeral conversation state in
        # a writable runtime directory, never in the source workspace.
        persistence_dir=Path(os.environ.get("OPENHANDS_PERSISTENCE_DIR", "/tmp/openhands-conversations")),
        callbacks=[on_event],
        visualizer=None,
        max_iteration_per_run=100,
    )
    conversation.send_message(args.prompt)
    conversation.run()
    final_response = get_agent_final_response(conversation.state.events)
    print("OPENHANDS_FINAL_START", flush=True)
    print(final_response or "OpenHands completed without a final response.", flush=True)
    print("OPENHANDS_FINAL_END", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"OPENHANDS_FAILURE {type(error).__name__}: {error}", file=sys.stderr, flush=True)
        raise
