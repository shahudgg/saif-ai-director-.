# SAIF ADVANCE EDITING ENGINE

Ye poora engine app ke andar bundled hai (`assets/saif_engine/`) — same file tree, same code.

| Part | Kya hai | Kaise chalta hai |
|---|---|---|
| `agy_ide_bridge/` | Go me likha IDE bridge: WebSocket hub, Claude providers, git service, plugins, attachments, voice | Termux: `pkg install golang` → `cd ~/saif_engine/agy_ide_bridge && go build -o saif-bridge . && ./saif-bridge` |
| `extensions/google-flow/` | Chrome MV3 extension: Flow client, workflows, storyboard analyzer, 65 MCP tools | ZIP ko Kiwi/desktop Chrome me "Load unpacked" karo |
| `termux-daemon/` | Go daemon jo Termux ke andar chalta hai | `go build -o saif-daemon ./termux-daemon` |
| `proto_generator/` | Python extractors + proto definitions | `python proto_generator/dump_all_local_types.py` |
| `extension_docs/`, `google-flow-mcp-spec.md`, `CLAUDE_CLI_PROTOCOL.md` | Poora MCP/extension spec aur examples | App ke andar hi padho (Docs tab) |

Credits: SAIF AI.
