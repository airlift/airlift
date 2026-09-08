#!/bin/sh

ADDRESS="$1"
shift

if [ -z "$MCP_CONFORMANCE_SCENARIO:-}" ]; then
    echo "MCP_CONFORMANCE_SCENARIO is not set" >&2
    exit 1
fi

curl $ADDRESS -d "{\"serverUri\": \"$1\", \"scenario\": \"$MCP_CONFORMANCE_SCENARIO\"}"
