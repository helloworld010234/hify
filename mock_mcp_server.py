#!/usr/bin/env python3
"""Minimal MCP mock server for testing Hify MCP integration."""

import json
from flask import Flask, request, Response

app = Flask(__name__)

TOOLS = [
    {
        "name": "query_order",
        "description": "Query order information by order ID",
        "inputSchema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "Order ID to query"}
            },
            "required": ["order_id"]
        }
    }
]


def make_jsonrpc_response(req_id, result):
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def make_jsonrpc_error(req_id, code, message):
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


@app.route("/mcp", methods=["POST", "GET"])
def mcp_endpoint():
    if request.method == "GET":
        return "MCP Mock Server OK", 200

    body = request.get_json(force=True, silent=True)
    if body is None:
        return Response(json.dumps(make_jsonrpc_error(None, -32700, "Parse error")),
                        mimetype="application/json")

    # Handle batch requests (list of messages)
    if isinstance(body, list):
        responses = [handle_single_message(msg) for msg in body]
        return Response(json.dumps([r for r in responses if r is not None]),
                        mimetype="application/json")
    else:
        response = handle_single_message(body)
        if response is None:
            return "", 204
        return Response(json.dumps(response), mimetype="application/json")


def handle_single_message(msg):
    if not isinstance(msg, dict):
        return make_jsonrpc_error(None, -32600, "Invalid Request")

    req_id = msg.get("id")
    method = msg.get("method")
    params = msg.get("params", {})

    if method == "initialize":
        return make_jsonrpc_response(req_id, {
            "protocolVersion": "2024-11-05",
            "serverInfo": {"name": "hify-mock-mcp", "version": "1.0.0"},
            "capabilities": {
                "tools": {"listChanged": False}
            }
        })

    if method == "notifications/initialized":
        # Notification, no response needed
        return None

    if method == "tools/list":
        return make_jsonrpc_response(req_id, {"tools": TOOLS})

    if method == "tools/call":
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        if tool_name == "query_order":
            order_id = arguments.get("order_id", "unknown")
            return make_jsonrpc_response(req_id, {
                "content": [
                    {"type": "text", "text": f"Order {order_id}: status=shipped, total=99.99, items=[item_A, item_B]"}
                ],
                "isError": False
            })
        return make_jsonrpc_response(req_id, {
            "content": [
                {"type": "text", "text": f"Tool '{tool_name}' executed with args: {json.dumps(arguments)}"}
            ],
            "isError": False
        })

    return make_jsonrpc_error(req_id, -32601, f"Method not found: {method}")


if __name__ == "__main__":
    print("Starting MCP Mock Server on http://localhost:9001/mcp")
    app.run(host="0.0.0.0", port=9001, threaded=True)
