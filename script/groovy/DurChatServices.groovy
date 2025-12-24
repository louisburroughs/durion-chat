package durion.chat

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.moqui.context.ExecutionContext

import java.net.HttpURLConnection
import java.net.URL

@CompileStatic
class DurChatServices {

    static Map<String, Object> startChatSession(ExecutionContext ec, Map<String, Object> context) {
        String partyId = (String) context.get("partyId")
        if (!partyId) {
            throw new IllegalArgumentException("partyId is required")
        }

        Map<String, Object> session = [
                chatSessionId   : ec.getEntity().sequencedIdPrimary("DurChatSession"),
                partyId         : partyId,
                startedDate     : ec.user.nowTimestamp,
                lastUpdatedStamp: ec.user.nowTimestamp,
                statusId        : "CHAT_ACTIVE"
        ]

        ec.entity.makeValue("DurChatSession", session).create()

        return [chatSessionId: session.chatSessionId]
    }

    static Map<String, Object> sendChatMessage(ExecutionContext ec, Map<String, Object> context) {
        String chatSessionId = (String) context.get("chatSessionId")
        String fromRoleTypeId = (String) context.get("fromRoleTypeId")
        String messageText = (String) context.get("messageText")

        if (!chatSessionId || !fromRoleTypeId || !messageText) {
            throw new IllegalArgumentException("chatSessionId, fromRoleTypeId and messageText are required")
        }

        Map<String, Object> msg = [
                chatMessageId : ec.entity.sequencedIdPrimary("DurChatMessage"),
                chatSessionId : chatSessionId,
                fromRoleTypeId: fromRoleTypeId,
                messageText   : messageText,
                sentDate      : ec.user.nowTimestamp
        ]
        ec.entity.makeValue("DurChatMessage", msg).create()

        // Only trigger an agent response when the message comes from the user.
        if ("USER" == fromRoleTypeId) {
            String agentReplyText = callMcpServer(ec, messageText)

            if (agentReplyText) {
                Map<String, Object> agentMsg = [
                        chatMessageId : ec.entity.sequencedIdPrimary("DurChatMessage"),
                        chatSessionId : chatSessionId,
                        fromRoleTypeId: "AGENT",
                        messageText   : agentReplyText,
                        sentDate      : ec.user.nowTimestamp
                ]
                ec.entity.makeValue("DurChatMessage", agentMsg).create()

                return [responseMessageId: agentMsg.chatMessageId]
            }
        }

        return [responseMessageId: msg.chatMessageId]
    }

    /**
     * Call the backend pos-mcp-server chat endpoint and return a textual reply.
     * <p>
     * The base URL should be externalized via configuration; the current
     * default assumes the MCP server is reachable on localhost.
     */
    private static String callMcpServer(ExecutionContext ec, String userMessage) {
        String baseUrl = System.getProperty("durion.mcp.baseUrl", "http://localhost:8085")
        String endpoint = baseUrl + "/api/mcp/chat"

        Map<String, Object> payload = [
                toolName : "positivity-ping",
                // Arguments are included for future tools that accept input;
                // the current ping tool ignores them safely.
                arguments: [
                        messageText: userMessage
                ]
        ]

        HttpURLConnection connection = null
        try {
            URL url = new URL(endpoint)
            connection = (HttpURLConnection) url.openConnection()
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setDoOutput(true)

            String jsonBody = JsonOutput.toJson(payload)
            connection.outputStream.withCloseable { it.write(jsonBody.getBytes("UTF-8")) }

            int status = connection.getResponseCode()
            InputStream stream = (status >= 200 && status < 300) ? connection.getInputStream() : connection.getErrorStream()
            if (stream == null) {
                return null
            }

            String body = stream.text
            // For now, return the raw JSON response as the agent message.
            // This keeps the integration simple and avoids coupling to
            // the specific ToolResponse JSON shape.
            return body
        } catch (Exception e) {
            ec.logger.error("Error calling MCP server at ${endpoint}", e)
            return null
        } finally {
            if (connection != null) {
                connection.disconnect()
            }
        }
    }
}
