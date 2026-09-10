#!/usr/bin/env python3
"""Dependency-free MCP stdio bridge and one-shot client for MythosScript."""
import argparse
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request


OFFLINE_INSTRUCTIONS = (
    "MythosScript tools are available before Minecraft starts. "
    "Call mythos_clients to check whether a game client is connected. "
    "Game-dependent calls made while Minecraft is stopped return a retryable "
    "GAME_NOT_RUNNING result; the bridge discovers a newly started client on the next call."
)


def _offline_tool(name, description, fields=(), required=()):
    """Build the stable tool manifest used before the game publishes its runtime backend."""
    properties = {}
    for field, field_type in fields:
        schema = {} if field_type == "any" else {"type": field_type}
        if field_type == "array":
            schema["items"] = {}
        properties[field] = schema
    properties.setdefault("pid", {"type": "integer"})
    properties.setdefault("player", {"type": "string"})
    return {
        "name": name,
        "description": description,
        "inputSchema": {
            "type": "object",
            "properties": properties,
            "required": list(required),
            "additionalProperties": False,
        },
        "annotations": {
            "readOnlyHint": name in {
                "mythos_clients", "mythos_discover", "mythos_status", "mythos_preflight",
                "mythos_snapshot", "mythos_chat", "mythos_actions", "mythos_templates",
                "mythos_validate", "mythos_logs", "mythos_wait", "mythos_packet_trace",
            },
            "destructiveHint": name not in {
                "mythos_clients", "mythos_discover", "mythos_status", "mythos_preflight",
                "mythos_snapshot", "mythos_chat", "mythos_actions", "mythos_templates",
                "mythos_validate", "mythos_logs", "mythos_wait", "mythos_packet_trace",
                "mythos_events",
            },
            "openWorldHint": True,
        },
    }


# Keep this manifest transport-local and independent from Minecraft classes. The live
# backend advertises the same public tools, while this copy lets stdio clients complete
# initialize/tools/list when no game process has created a discovery endpoint yet.
OFFLINE_TOOLS = [
    _offline_tool("mythos_clients", "List connected Minecraft clients, in-world usernames, PIDs and server information.", (("player", "string"),)),
    _offline_tool("mythos_discover", "Discover the active profile, configuration root, modules and supported operations.", ()),
    _offline_tool("mythos_status", "Read current server, player, nearby entities, active runs, variables and temporary state.", ()),
    _offline_tool("mythos_preflight", "Validate a saved or inline sequence and check world/server readiness.", (("name", "string"), ("sequence", "object"))),
    _offline_tool("mythos_events", "Configure and read the grouped event timeline, watches, filters and cursors.", (("operation", "string"), ("groups", "array"), ("types", "array"), ("entityIds", "array"), ("watchIds", "array"), ("fromMs", "integer"), ("toMs", "integer"), ("afterId", "integer"), ("sessionId", "string"), ("limit", "integer"), ("waitMs", "integer"), ("min", "array"), ("max", "array"), ("query", "string"), ("sampleTicks", "integer"), ("size", "array"), ("watchId", "string"), ("filter", "object"))),
    _offline_tool("mythos_snapshot", "Read player, inventory, entities, GUI and compact world snapshots.", (("groups", "array"), ("size", "array"), ("origin", "array"), ("entityIds", "array"), ("includeNbt", "boolean"))),
    _offline_tool("mythos_gui", "Inspect and control Minecraft GUI screens using stable semantic element paths.", (("operation", "string"), ("target", "string"), ("path", "string"), ("command", "string"), ("text", "string"), ("value", "any"), ("append", "boolean"), ("button", "string"), ("matchMode", "string"), ("x", "integer"), ("y", "integer"), ("key", "string"), ("keyCode", "integer"), ("character", "string"), ("state", "string"), ("pressDurationTicks", "integer"), ("wheel", "integer"), ("scope", "string")), ("operation",)),
    _offline_tool("mythos_chat", "Read received or displayed chat messages with filters, cursors and component metadata.", (("afterId", "integer"), ("connectionId", "string"), ("stream", "string"), ("type", "string"), ("query", "string"), ("limit", "integer"), ("includeFormatted", "boolean"), ("includeComponent", "boolean"))),
    _offline_tool("mythos_notes", "Read or write the per-server Tools-menu Markdown notebook.", (("operation", "string"), ("server", "string"), ("text", "string"), ("expectedHash", "string"))),
    _offline_tool("mythos_actions", "List or describe action IDs, fields, choices, defaults and runtime parameter references.", (("type", "string"), ("query", "string"), ("includeSource", "boolean"))),
    _offline_tool("mythos_templates", "List or get reusable GUI, teleport and retry action-template recipes.", (("id", "string"), ("query", "string"), ("category", "string"), ("includeActions", "boolean"))),
    _offline_tool("mythos_config", "Read, write, patch or delete persistent mod configuration files.", (("operation", "string"), ("path", "string"), ("value", "any"), ("text", "string"), ("patch", "array"), ("expectedHash", "string")), ("operation",)),
    _offline_tool("mythos_modules", "List, inspect, reload or persist writable configuration module fields.", (("operation", "string"), ("module", "string"), ("fields", "object"), ("persist", "boolean")), ("operation",)),
    _offline_tool("mythos_paths", "Create, read, update or delete path sequences, steps and actions.", (("operation", "string"), ("name", "string"), ("sequence", "object"), ("step", "object"), ("action", "object"), ("stepIndex", "integer"), ("actionIndex", "integer"), ("insert", "boolean")), ("operation",)),
    _offline_tool("mythos_run", "Run a saved path or inline sequence/actions and return an execution session ID.", (("name", "string"), ("sequence", "object"), ("actions", "array"), ("loops", "integer"), ("startStep", "integer"), ("startAction", "integer"), ("variables", "object"), ("replace", "boolean"))),
    _offline_tool("mythos_control", "Pause, resume or stop foreground/background/all path runs.", (("operation", "string"), ("scope", "string")), ("operation",)),
    _offline_tool("mythos_kill_aura", "Describe, start or stop a temporary kill-aura override with automatic restoration.", (("operation", "string"), ("fields", "object"), ("enabled", "boolean"), ("durationTicks", "integer")), ("operation",)),
    _offline_tool("mythos_packets", "Capture, list or clear C2S/S2C packet traces.", (("operation", "string"), ("direction", "string"), ("offset", "integer"), ("limit", "integer")), ("operation",)),
    _offline_tool("mythos_packet_trace", "Correlate input timeline events with nearby captured packets.", (("inputs", "array"), ("directions", "array"), ("direction", "string"), ("types", "array"), ("keys", "array"), ("keyCodes", "array"), ("buttons", "array"), ("eventIds", "array"), ("sessionIds", "array"), ("actions", "array"), ("guiTitles", "array"), ("screenNames", "array"), ("fromMs", "integer"), ("toMs", "integer"), ("query", "string"), ("inputLimit", "integer"), ("windowMs", "integer"), ("before", "integer"), ("after", "integer"), ("packetLimit", "integer"), ("maxTotalPackets", "integer"), ("includeHex", "boolean"), ("includeDecoded", "boolean"))),
    _offline_tool("mythos_validate", "Validate an inline sequence or all saved paths without executing them.", (("sequence", "object"),)),
    _offline_tool("mythos_logs", "Read execution session summaries, completion results and detailed logs.", (("sessionId", "string"), ("limit", "integer"), ("includeEvents", "boolean"))),
    _offline_tool("mythos_wait", "Wait for an execution session without blocking the Minecraft client thread.", (("sessionId", "string"), ("waitMs", "integer"), ("includeEvents", "boolean")), ("sessionId",)),
    _offline_tool("mythos_navigation", "List or execute built-in navigation commands such as goto, mine, build, farm and cancel.", (("command", "string"),)),
]

def discovery_path(explicit=None):
    configured = explicit or os.environ.get("MYTHOSSCRIPT_MCP_DISCOVERY_FILE")
    if configured:
        return pathlib.Path(configured)
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        return pathlib.Path(local_app_data) / "MythosScript" / "mcp-endpoints.json"
    return pathlib.Path.home() / ".mythosscript" / "mcp-endpoints.json"

def discover_connections(path=None):
    registry = discovery_path(path)
    try:
        root = json.loads(registry.read_text(encoding="utf-8-sig"))
        entries = root.get("entries", [])
    except (OSError, ValueError):
        entries = []
    if not isinstance(entries, list):
        entries = []
    candidates = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        endpoint = entry.get("endpoint")
        token_file = entry.get("tokenFile")
        if not isinstance(endpoint, str) or not endpoint:
            continue
        if not isinstance(token_file, str) or not token_file:
            continue
        if not pathlib.Path(token_file).is_file():
            continue
        candidates.append((0 if str(entry.get("role", "")).upper() == "HUB" else 1,
                           -int(entry.get("updatedAt", 0) or 0), endpoint, token_file))
    candidates.sort()
    return [(endpoint, pathlib.Path(token_file)) for _, __, endpoint, token_file in candidates]

def parse_pid(value):
    if value is None or value == "":
        return None
    if str(value).strip().lower() == "all":
        return -1
    return int(value)

class LocalClient:
    def __init__(self, token_file=None, url=None, pid=None, player=None, auto_discover=False,
                 discovery_file=None):
        from urllib.parse import urlparse
        self.url = url or ("http://127.0.0.1:8765/mcp" if token_file else None)
        if self.url:
            parsed = urlparse(self.url)
            if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
                raise ValueError("The bridge only connects to a local HTTP endpoint")
        self.token_file = pathlib.Path(token_file) if token_file else None
        self.pid = pid
        self.player = player
        self.auto_discover = auto_discover or (self.token_file is None and self.url is None)
        self.discovery_file = discovery_file
        # Do not route local bearer credentials through system proxy settings.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    @staticmethod
    def _offline_capable(message):
        if not isinstance(message, dict):
            return False
        return message.get("method") in {"initialize", "ping", "tools/list", "tools/call"}

    @staticmethod
    def _offline_response(message):
        """Return a valid MCP response while the game-side backend is absent."""
        method = message.get("method")
        request_id = message.get("id")
        if method == "initialize":
            params = message.get("params") if isinstance(message.get("params"), dict) else {}
            requested = params.get("protocolVersion", "")
            version = requested if requested in {"2025-06-18", "2025-03-26", "2024-11-05"} else "2025-03-26"
            result = {
                "protocolVersion": version,
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": "MythosScript", "version": "1.0.0"},
                "instructions": OFFLINE_INSTRUCTIONS,
            }
        elif method == "ping":
            result = {}
        elif method == "tools/list":
            result = {"tools": OFFLINE_TOOLS}
        elif method == "tools/call":
            params = message.get("params") if isinstance(message.get("params"), dict) else {}
            name = params.get("name", "")
            data = {
                "available": False,
                "connected": False,
                "gameRunning": False,
                "retryable": True,
                "errorCode": "GAME_NOT_RUNNING",
                "message": "Minecraft is not running or MythosScript is not loaded. Start the game; this bridge will connect automatically on the next call.",
            }
            # This is a normal empty discovery result, not a failed tool call.
            if name == "mythos_clients":
                data.update({"players": [], "clients": [], "role": "offline", "localPid": None})
                is_error = False
            else:
                is_error = True
            result = {
                "content": [{"type": "text", "text": json.dumps(data, ensure_ascii=False)}],
                "structuredContent": data,
                "isError": is_error,
            }
        else:
            return None
        return {"jsonrpc": "2.0", "id": request_id, "result": result}

    def connections(self):
        if self.auto_discover:
            found = discover_connections(self.discovery_file)
            if not found:
                raise RuntimeError("No running MythosScript client was discovered. Launch the mod first.")
            return found
        if self.token_file is None or self.url is None:
            raise RuntimeError("--token-file and --url are required unless --auto-discover is enabled")
        return [(self.url, self.token_file)]

    @staticmethod
    def _unique_connections(candidates):
        result = []
        seen = set()
        for url, token_file in candidates:
            key = (url, str(token_file))
            if key not in seen:
                seen.add(key)
                result.append((url, token_file))
        return result

    def _request(self, url, token_file, message):
        token = token_file.read_text(encoding="utf-8-sig").strip()
        request = urllib.request.Request(
            url, data=json.dumps(message, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json",
                     "Accept": "application/json, text/event-stream",
                     "Authorization": "Bearer " + token}, method="POST")
        method = message.get("method") if isinstance(message, dict) else None
        params = message.get("params") if isinstance(message, dict) else None
        tool_name = params.get("name") if isinstance(params, dict) else None
        fast_probe = method != "tools/call" or tool_name == "mythos_clients"
        timeout = 5 if self.auto_discover and fast_probe else 40
        with self.opener.open(request, timeout=timeout) as response:
            body = response.read()
            return json.loads(body) if body else None

    @staticmethod
    def _structured(response):
        if not isinstance(response, dict):
            return None
        result = response.get("result")
        if not isinstance(result, dict) or result.get("isError"):
            return None
        value = result.get("structuredContent")
        if isinstance(value, dict):
            return value
        for item in result.get("content", []):
            if isinstance(item, dict) and isinstance(item.get("text"), str):
                try:
                    parsed = json.loads(item["text"])
                    if isinstance(parsed, dict):
                        return parsed
                except ValueError:
                    pass
        return None

    def _client_records(self, candidates):
        records = []
        seen_endpoints = set()
        for url, token_file in self._unique_connections(candidates):
            if url in seen_endpoints:
                continue
            try:
                response = self._request(url, token_file, {
                    "jsonrpc": "2.0", "id": "discover-clients",
                    "method": "tools/call",
                    "params": {"name": "mythos_clients", "arguments": {}}})
            except (OSError, urllib.error.HTTPError, urllib.error.URLError):
                continue
            data = self._structured(response)
            if not isinstance(data, dict):
                continue
            seen_endpoints.add(url)
            for client in data.get("clients", []):
                if not isinstance(client, dict):
                    continue
                row = dict(client)
                row["_endpoint"] = url
                row["_token_file"] = token_file
                records.append(row)
        return records

    @staticmethod
    def _client_description(client):
        return "pid={pid}, player={player}, server={server}, inWorld={inWorld}".format(
            pid=client.get("pid", "?"), player=client.get("player") or "<none>",
            server=client.get("server") or "<unknown>", inWorld=client.get("inWorld", False))

    def _route_targets(self, message, candidates):
        params = message.get("params") if isinstance(message, dict) else None
        arguments = params.get("arguments") if isinstance(params, dict) else None
        arguments = arguments if isinstance(arguments, dict) else {}
        pid = arguments.get("pid")
        player = arguments.get("player")
        if pid is not None:
            try:
                pid = int(pid)
            except (TypeError, ValueError):
                pass
        if pid == -1 or (isinstance(player, str) and player.lower() in ("all", "*")):
            return self._unique_connections(candidates)

        records = self._client_records(candidates)
        matches = []
        if pid is not None:
            matches = [row for row in records if row.get("pid") == pid]
        elif isinstance(player, str) and player.strip():
            needle = player.strip().casefold()
            exact = [row for row in records if str(row.get("player") or "").casefold() == needle]
            matches = exact or [row for row in records
                                if needle in str(row.get("player") or "").casefold()]
        else:
            in_world = [row for row in records if row.get("inWorld")]
            matches = in_world if len(in_world) == 1 else (records if len(records) == 1 else [])

        if len(matches) == 1:
            return [(matches[0]["_endpoint"], matches[0]["_token_file"])]
        if len(matches) > 1:
            details = "; ".join(self._client_description(row) for row in matches)
            raise RuntimeError("Ambiguous MythosScript target; pass pid. Candidates: " + details)
        if not records:
            raise RuntimeError("No running MythosScript clients were discovered")
        details = "; ".join(self._client_description(row) for row in records)
        raise RuntimeError("No MythosScript client matches the requested target. Available: " + details)

    def _aggregate_clients(self, message, candidates):
        records = self._client_records(candidates)
        if not records:
            return self._offline_response(message)
        params = message.get("params", {})
        arguments = params.get("arguments", {}) if isinstance(params, dict) else {}
        player_filter = arguments.get("player") if isinstance(arguments, dict) else None
        pid_filter = arguments.get("pid") if isinstance(arguments, dict) else None
        if isinstance(player_filter, str) and player_filter.lower() not in ("", "all", "*"):
            needle = player_filter.casefold()
            records = [row for row in records if needle in str(row.get("player") or "").casefold()
                       or needle in str(row.get("pid") or "").casefold()]
        if pid_filter not in (None, -1, "-1"):
            try:
                records = [row for row in records if row.get("pid") == int(pid_filter)]
            except (TypeError, ValueError):
                records = []
        clients = []
        players = []
        seen_pids = set()
        seen_players = set()
        for row in records:
            pid = row.get("pid")
            if pid in seen_pids:
                continue
            seen_pids.add(pid)
            clean = {key: value for key, value in row.items() if not key.startswith("_")}
            clean["mcpEndpoint"] = row.get("_endpoint")
            clients.append(clean)
            name = row.get("player")
            if row.get("inWorld") and name and name not in seen_players:
                seen_players.add(name)
                players.append(name)
        merged = {"localPid": -1 if len({row.get('_endpoint') for row in records}) > 1 else (clients[0].get("pid") if clients else None),
                  "role": "multi-endpoint", "players": players, "clients": clients}
        return {"jsonrpc": "2.0", "id": message.get("id"),
                "result": {"content": [{"type": "text", "text": json.dumps(merged, ensure_ascii=False)}],
                            "structuredContent": merged, "isError": False}}

    def send(self, message):
        if not isinstance(message, dict):
            raise RuntimeError("MCP message must be a JSON object")
        # Notifications do not need a game-side round trip. In particular, this
        # keeps notifications/initialized harmless during offline startup.
        if "id" not in message:
            return None
        if isinstance(message, dict) and message.get("method") == "tools/call" and (self.pid is not None or self.player):
            params = message.setdefault("params", {})
            arguments = params.get("arguments")
            if not isinstance(arguments, dict):
                arguments = {}
                params["arguments"] = arguments
            if self.pid is not None:
                arguments.setdefault("pid", self.pid)
            if self.player:
                arguments.setdefault("player", self.player)
        try:
            candidates = self.connections()
        except RuntimeError:
            if self.auto_discover and self._offline_capable(message):
                return self._offline_response(message)
            raise
        if (isinstance(message, dict) and message.get("method") == "tools/call"
                and isinstance(message.get("params"), dict)):
            tool_name = message["params"].get("name")
            if tool_name == "mythos_clients" and self.auto_discover:
                return self._aggregate_clients(message, candidates)
            if self.auto_discover:
                try:
                    candidates = self._route_targets(message, candidates)
                except RuntimeError as error:
                    if str(error) == "No running MythosScript clients were discovered":
                        return self._offline_response(message)
                    raise
        last_error = None
        for url, token_file in self._unique_connections(candidates):
            try:
                return self._request(url, token_file, message)
            except (OSError, urllib.error.HTTPError, urllib.error.URLError) as error:
                last_error = error
                if not self.auto_discover:
                    break
        if self.auto_discover and self._offline_capable(message):
            return self._offline_response(message)
        if isinstance(last_error, urllib.error.HTTPError):
            raise RuntimeError("MCP HTTP error " + str(last_error.code)) from None
        raise RuntimeError("Cannot reach MythosScript. Launch the rebuilt mod and check the port.") from None

def bridge(client, incoming, outgoing):
    for line in incoming:
        if not line.strip():
            continue
        message = None
        try:
            message = json.loads(line)
            response = client.send(message)
        except Exception as error:
            if isinstance(message, dict) and "id" not in message:
                print(str(error), file=sys.stderr)
                continue
            response = {"jsonrpc": "2.0", "id": message.get("id") if isinstance(message, dict) else None,
                        "error": {"code": -32000, "message": str(error)}}
        if response is not None:
            outgoing.write(json.dumps(response, ensure_ascii=False) + "\n")
            outgoing.flush()

def follow_events(client, arguments, duration, outgoing):
    """Cursor-based MCP long poll, rendered as NDJSON events and explicit checkpoints."""
    args = dict(arguments)
    args['operation'] = 'read'
    args['waitMs'] = 0
    def call(params):
        response = client.send({'jsonrpc':'2.0', 'id':'events', 'method':'tools/call',
                                'params':{'name':'mythos_events', 'arguments':params}})
        if response.get('error') or response.get('result',{}).get('isError'):
            raise RuntimeError(json.dumps(response, ensure_ascii=False))
        return response['result']['structuredContent']
    def write(value):
        outgoing.write(json.dumps(value, ensure_ascii=False) + '\n')
        outgoing.flush()
    # Without an explicit cursor, start at now instead of replaying old gameplay.
    if 'afterId' not in args:
        baseline = call(dict(args, limit=1))
        args.update(afterId=baseline['nextAfterId'], sessionId=baseline['sessionId'])
        write({'kind':'checkpoint', **{k:baseline[k] for k in ('sessionId','nextAfterId','connected')}})
    deadline = time.monotonic() + duration if duration > 0 else float('inf')
    while time.monotonic() < deadline:
        args['waitMs'] = min(25000, max(0, int((deadline-time.monotonic())*1000))) if duration>0 else 25000
        result = call(args)
        for event in result['events']:
            write(event)
        write({'kind':'checkpoint', **{k:result[k] for k in ('sessionId','nextAfterId','connected','hasMore','sessionChanged','cursorExpired','cursorAhead')}})
        args.update(afterId=result['nextAfterId'], sessionId=result['sessionId'])
        if not result['connected']:
            time.sleep(min(1, max(0, deadline-time.monotonic())))

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--token-file", default=os.environ.get("MYTHOSSCRIPT_MCP_TOKEN_FILE"),
                        help="Game directory/config/我的世界脚本/mcp.token")
    parser.add_argument("--url", default=os.environ.get("MYTHOSSCRIPT_MCP_URL"),
                        help="Local MCP endpoint; omitted with --auto-discover")
    parser.add_argument("--auto-discover", action="store_true",
                        help="Discover the running MythosScript JAR without a game directory path")
    parser.add_argument("--discovery-file", default=os.environ.get("MYTHOSSCRIPT_MCP_DISCOVERY_FILE"),
                        help="Override the machine-local discovery registry")
    parser.add_argument("--pid", default=os.environ.get("MYTHOSSCRIPT_MCP_PID"),
                        help="Target client pid, or 'all' to broadcast (-1)")
    parser.add_argument("--player", default=os.environ.get("MYTHOSSCRIPT_MCP_PLAYER"),
                        help="Target in-world username, or 'all' to broadcast")
    sub = parser.add_subparsers(dest="mode")
    sub.add_parser("stdio", help="Serve newline-delimited JSON-RPC over stdin/stdout")
    sub.add_parser("probe", help="Check initialize and list advertised tools")
    call = sub.add_parser("call", help="Invoke one tool and output its result")
    call.add_argument("tool")
    call.add_argument("--arguments", default="{}", help="JSON arguments object")
    call.add_argument("--arguments-file", help="UTF-8 JSON arguments file")
    watch = sub.add_parser("watch", help="Continuously read MCP events and print NDJSON; Ctrl-C stops")
    watch.add_argument("--arguments", default="{}", help="mythos_events read filters and optional cursor")
    watch.add_argument("--arguments-file", help="UTF-8 JSON filters file")
    watch.add_argument("--duration", type=float, default=0, help="Seconds; 0 follows until interrupted")
    args = parser.parse_args(argv)
    if not args.token_file and not args.url:
        args.auto_discover = True
    if not args.auto_discover and not args.token_file:
        parser.error("--token-file or --auto-discover is required")
    try:
        pid = parse_pid(args.pid)
    except ValueError:
        parser.error("--pid must be an integer or 'all'")
    player = args.player.strip() if args.player else None
    client = LocalClient(args.token_file, args.url, pid, player, args.auto_discover, args.discovery_file)
    if args.mode in (None, "stdio"):
        bridge(client, sys.stdin, sys.stdout)
        return 0
    client.send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
                 "params": {"protocolVersion": "2025-03-26", "capabilities": {},
                            "clientInfo": {"name": "mythosscript-client", "version": "1.0"}}})
    client.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
    if args.mode == 'watch':
        arguments = json.loads(pathlib.Path(args.arguments_file).read_text(encoding='utf-8-sig')
                               if args.arguments_file else args.arguments)
        if not isinstance(arguments, dict) or args.duration < 0:
            parser.error('watch requires an object and nonnegative duration')
        try:
            follow_events(client, arguments, args.duration, sys.stdout)
        except KeyboardInterrupt:
            pass
        return 0
    if args.mode == "probe":
        message = {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
    else:
        arguments = json.loads(pathlib.Path(args.arguments_file).read_text(encoding="utf-8-sig")
                               if args.arguments_file else args.arguments)
        if not isinstance(arguments, dict):
            parser.error("arguments must be a JSON object")
        message = {"jsonrpc": "2.0", "id": 2, "method": "tools/call",
                   "params": {"name": args.tool, "arguments": arguments}}
    response = client.send(message)
    print(json.dumps(response, ensure_ascii=False, indent=2))
    return 1 if response.get("error") or response.get("result", {}).get("isError") else 0

if __name__ == "__main__":
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
        sys.stdout.reconfigure(encoding="utf-8")
    try:
        raise SystemExit(main())
    except Exception as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
