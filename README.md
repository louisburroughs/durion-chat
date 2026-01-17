# durion-chat
Chat interface for a backend MCP server

## Purpose

Provide a Moqui-hosted chat UI and supporting services to interact with an MCP-backed assistant/server for operational support, diagnostics, and guided workflows.

## Scope

In scope:
- UI for chat sessions, message history presentation, and conversation context
- Integration with an MCP server endpoint (request/response, session metadata)
- Operator-friendly affordances (copy/export, links to related records) where implemented

Out of scope:
- Authoritative system-of-record persistence for business entities
- Security policy definition (must rely on platform authentication/authorization)
- Automated execution of privileged actions without explicit authorization and audit trails
