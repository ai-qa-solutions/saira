import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import type { SpanDetail } from "@/types/trace";
import type { ShadowResult } from "@/types/shadow";
import ShadowChatResponse from "@/components/shadow/ShadowChatResponse";
import {
  User,
  Bot,
  ChevronDown,
  ChevronRight,
  Wrench,
  Settings,
} from "lucide-react";

/** A single message in the OpenAI chat format. */
interface ChatMessage {
  role: "system" | "user" | "assistant" | "tool" | "function";
  content: string | null;
  tool_calls?: ToolCall[];
  tool_call_id?: string;
  name?: string;
}

interface ToolCall {
  id: string;
  type: "function";
  function: {
    name: string;
    arguments: string;
  };
}

/** Delta from an SSE chunk. */
interface DeltaFunctionCall {
  name?: string;
  arguments?: string | Record<string, unknown>;
}

interface Delta {
  role?: string;
  content?: string | null;
  tool_calls?: DeltaToolCall[];
  function_call?: DeltaFunctionCall;
}

interface DeltaToolCall {
  index: number;
  id?: string;
  type?: string;
  function?: {
    name?: string;
    arguments?: string;
  };
}

/**
 * Attempts to parse a JSON response body as a non-streaming chat completion.
 * Returns the first choice's message if valid, null otherwise.
 */
function parseNonStreamingResponse(raw: string): ChatMessage | null {
  try {
    const json = JSON.parse(raw);
    const message = json?.choices?.[0]?.message;
    if (message && typeof message.role === "string") {
      const result = message as ChatMessage;

      // GigaChat format: convert function_call to tool_calls
      if (!result.tool_calls && message.function_call) {
        const fc = message.function_call;
        const args =
          typeof fc.arguments === "string"
            ? fc.arguments
            : fc.arguments != null
              ? JSON.stringify(fc.arguments)
              : "";
        result.tool_calls = [
          {
            id: `fc-${fc.name}`,
            type: "function",
            function: { name: fc.name, arguments: args },
          },
        ];
      }

      return result;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Parses an SSE response body (lines starting with "data: ") into a single
 * assistant message by concatenating content deltas and accumulating tool_calls.
 */
function parseSseResponse(raw: string): ChatMessage | null {
  const lines = raw.split("\n");
  let content = "";
  const toolCallMap = new Map<
    number,
    { id: string; name: string; arguments: string }
  >();
  let foundChunks = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("data: ")) continue;
    const payload = trimmed.slice(6);
    if (payload === "[DONE]") continue;

    try {
      const chunk = JSON.parse(payload);
      const delta: Delta | undefined = chunk?.choices?.[0]?.delta;
      if (!delta) continue;
      foundChunks = true;

      if (delta.content) {
        content += delta.content;
      }

      // OpenAI format: delta.tool_calls
      if (delta.tool_calls) {
        for (const tc of delta.tool_calls) {
          const existing = toolCallMap.get(tc.index);
          if (existing) {
            if (tc.function?.arguments) {
              existing.arguments += tc.function.arguments;
            }
          } else {
            toolCallMap.set(tc.index, {
              id: tc.id ?? "",
              name: tc.function?.name ?? "",
              arguments: tc.function?.arguments ?? "",
            });
          }
        }
      }

      // GigaChat format: delta.function_call
      if (delta.function_call) {
        const fc = delta.function_call;
        const existing = toolCallMap.get(0);
        const argsStr =
          typeof fc.arguments === "string"
            ? fc.arguments
            : fc.arguments != null
              ? JSON.stringify(fc.arguments)
              : "";
        if (existing) {
          if (fc.name) existing.name = fc.name;
          if (argsStr) existing.arguments += argsStr;
        } else {
          toolCallMap.set(0, {
            id: `fc-${fc.name ?? "call"}`,
            name: fc.name ?? "",
            arguments: argsStr,
          });
        }
      }
    } catch {
      // skip malformed chunk
    }
  }

  if (!foundChunks) return null;

  const toolCalls: ToolCall[] = [];
  const sortedEntries = Array.from(toolCallMap.entries()).sort(
    ([a], [b]) => a - b,
  );
  for (const [, val] of sortedEntries) {
    toolCalls.push({
      id: val.id,
      type: "function",
      function: { name: val.name, arguments: val.arguments },
    });
  }

  const message: ChatMessage = {
    role: "assistant",
    content: content || null,
  };
  if (toolCalls.length > 0) {
    message.tool_calls = toolCalls;
  }
  return message;
}

/**
 * Extracts the assistant response from the response body,
 * trying non-streaming JSON first, then SSE format.
 */
function parseResponseBody(raw: string | null): ChatMessage | null {
  if (!raw) return null;
  return parseNonStreamingResponse(raw) ?? parseSseResponse(raw);
}

/**
 * Extracts a function_call or tool_calls from a span's response body
 * and normalizes to ToolCall[] format.
 */
function extractToolCallsFromResponse(
  responseBody: string | null,
): ToolCall[] | null {
  if (!responseBody) return null;
  try {
    const json = JSON.parse(responseBody);
    const msg = json?.choices?.[0]?.message;
    if (!msg) return null;

    // New format: tool_calls array
    if (Array.isArray(msg.tool_calls) && msg.tool_calls.length > 0) {
      return msg.tool_calls as ToolCall[];
    }

    // Old format: function_call object
    if (msg.function_call) {
      const fc = msg.function_call;
      const args =
        typeof fc.arguments === "string"
          ? fc.arguments
          : JSON.stringify(fc.arguments);
      return [
        {
          id: `fc-${fc.name}`,
          type: "function",
          function: { name: fc.name, arguments: args },
        },
      ];
    }
    return null;
  } catch {
    return null;
  }
}

/** Result of extracting conversation: messages and message-to-span mapping. */
interface ConversationData {
  messages: ChatMessage[];
  /** Maps message index to the span that produced this assistant response. */
  messageSpanMap: Map<number, SpanDetail>;
}

/**
 * Finds the last span with valid requestBody.messages, extracts the full
 * conversation, and enriches assistant messages with tool_calls from
 * earlier span responses.
 *
 * Each span[N] has N*2+2 messages in its request and its response is the
 * assistant turn that becomes message at index (request.messages.length)
 * in subsequent spans. We use this to map span responses back to messages.
 *
 * Also returns a messageSpanMap that maps each assistant message index to
 * its source span, enabling shadow result lookups by span ID.
 */
function extractConversation(spans: SpanDetail[]): ConversationData {
  // Build a map: messageCount -> span (for looking up responses)
  const spanByMsgCount = new Map<number, SpanDetail>();
  let lastSpanWithMessages: SpanDetail | null = null;

  for (const span of spans) {
    if (!span.requestBody) continue;
    try {
      const body = JSON.parse(span.requestBody);
      if (Array.isArray(body?.messages) && body.messages.length > 0) {
        spanByMsgCount.set(body.messages.length, span);
        lastSpanWithMessages = span;
      }
    } catch {
      // skip
    }
  }

  if (!lastSpanWithMessages) return { messages: [], messageSpanMap: new Map() };

  const body = JSON.parse(lastSpanWithMessages.requestBody!);
  const messages: ChatMessage[] = body.messages;

  // Normalize GigaChat function_call → tool_calls for all messages
  for (const msg of messages) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const raw = msg as any;
    if (!msg.tool_calls && raw.function_call) {
      const fc = raw.function_call;
      const args =
        typeof fc.arguments === "string"
          ? fc.arguments
          : fc.arguments != null
            ? JSON.stringify(fc.arguments)
            : "";
      msg.tool_calls = [
        {
          id: `fc-${fc.name}`,
          type: "function",
          function: { name: fc.name, arguments: args },
        },
      ];
    }
  }

  // Parse the final response and append
  const assistantResponse = parseResponseBody(
    lastSpanWithMessages.responseBody,
  );
  if (assistantResponse) {
    messages.push(assistantResponse);
  }

  // Build message-to-span mapping for assistant messages
  const messageSpanMap = new Map<number, SpanDetail>();

  // Enrich assistant messages with tool_calls from earlier span responses
  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i];
    if (msg.role !== "assistant") continue;

    // This assistant message was the response of the span whose request had `i` messages
    const sourceSpan = spanByMsgCount.get(i);
    if (sourceSpan) {
      messageSpanMap.set(i, sourceSpan);

      if (!msg.tool_calls || msg.tool_calls.length === 0) {
        const toolCalls = extractToolCallsFromResponse(sourceSpan.responseBody);
        if (toolCalls) {
          msg.tool_calls = toolCalls;
        }
      }
    }
  }

  // Map the last assistant message to the last span if not already mapped
  const lastMsgIdx = messages.length - 1;
  if (
    messages[lastMsgIdx]?.role === "assistant" &&
    !messageSpanMap.has(lastMsgIdx)
  ) {
    messageSpanMap.set(lastMsgIdx, lastSpanWithMessages);
  }

  return { messages, messageSpanMap };
}

/**
 * Formats a JSON string for display. Returns prettified JSON or original string.
 */
function formatArgs(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

/** Collapsible section component. */
function Collapsible({
  label,
  defaultOpen = false,
  className,
  children,
}: {
  label: string;
  defaultOpen?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className={cn("rounded-lg border", className)}>
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-xs font-medium hover:bg-muted/50 transition-colors"
        onClick={() => setOpen(!open)}
      >
        {open ? (
          <ChevronDown className="size-3.5" />
        ) : (
          <ChevronRight className="size-3.5" />
        )}
        {label}
      </button>
      {open && <div className="px-3 pb-3">{children}</div>}
    </div>
  );
}

/** Renders a system message as a collapsible banner. */
function SystemMessage({ content }: { content: string }) {
  return (
    <Collapsible
      label="System prompt"
      className="mx-4 border-violet-500/30 bg-violet-500/5"
    >
      <pre className="text-xs font-mono whitespace-pre-wrap break-words text-muted-foreground">
        {content}
      </pre>
    </Collapsible>
  );
}

/** Renders a user message as a right-aligned bubble. */
function UserMessage({ content }: { content: string }) {
  return (
    <div className="flex justify-end gap-2 px-4">
      <div className="max-w-[80%] rounded-2xl rounded-tr-sm bg-primary px-4 py-2.5 text-primary-foreground">
        <pre className="text-sm whitespace-pre-wrap break-words font-sans">
          {content}
        </pre>
      </div>
      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10">
        <User className="size-4 text-primary" />
      </div>
    </div>
  );
}

/** Renders an assistant text message as a left-aligned bubble. */
function AssistantMessage({ content }: { content: string }) {
  return (
    <div className="flex gap-2 px-4">
      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted">
        <Bot className="size-4 text-muted-foreground" />
      </div>
      <div className="max-w-[80%] rounded-2xl rounded-tl-sm bg-muted px-4 py-2.5">
        <pre className="text-sm whitespace-pre-wrap break-words font-sans">
          {content}
        </pre>
      </div>
    </div>
  );
}

/** Renders tool call cards below an optional text bubble. */
function AssistantToolCallMessage({
  content,
  toolCalls,
}: {
  content: string | null;
  toolCalls: ToolCall[];
}) {
  return (
    <div className="flex gap-2 px-4">
      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted">
        <Bot className="size-4 text-muted-foreground" />
      </div>
      <div className="max-w-[80%] space-y-2">
        {content && (
          <div className="rounded-2xl rounded-tl-sm bg-muted px-4 py-2.5">
            <pre className="text-sm whitespace-pre-wrap break-words font-sans">
              {content}
            </pre>
          </div>
        )}
        {toolCalls.map((tc) => (
          <Collapsible
            key={tc.id}
            label={`Tool call: ${tc.function.name}`}
            className="border-orange-500/40 bg-orange-500/5 [&_button]:text-orange-700 dark:[&_button]:text-orange-400"
          >
            {tc.function.arguments && (
              <pre className="text-xs font-mono whitespace-pre-wrap break-words text-muted-foreground">
                {formatArgs(tc.function.arguments)}
              </pre>
            )}
          </Collapsible>
        ))}
      </div>
    </div>
  );
}

/** Renders a compact indicator for an assistant calling a function (old format without args). */
function FunctionCallIndicator({ functionName }: { functionName: string }) {
  return (
    <div className="flex gap-2 px-4">
      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted">
        <Bot className="size-4 text-muted-foreground" />
      </div>
      <div className="flex items-center gap-1.5 rounded-lg border border-orange-500/40 bg-orange-500/5 px-3 py-1.5">
        <Wrench className="size-3.5 text-orange-700 dark:text-orange-400" />
        <span className="text-xs font-medium text-orange-700 dark:text-orange-400">
          Called {functionName}
        </span>
      </div>
    </div>
  );
}

/** Renders a tool result message. */
function ToolMessage({
  name,
  content,
}: {
  name: string | undefined;
  content: string;
}) {
  return (
    <div className="flex gap-2 px-4">
      <div className="size-7 shrink-0" />
      <div className="max-w-[80%]">
        <Collapsible
          label={name ? `Tool result: ${name}` : "Tool result"}
          defaultOpen={false}
          className="border-green-500/40 bg-green-500/5"
        >
          <pre className="text-xs font-mono whitespace-pre-wrap break-words text-muted-foreground">
            {formatArgs(content)}
          </pre>
        </Collapsible>
      </div>
    </div>
  );
}

/** Props for the ChatView component. */
interface ChatViewProps {
  spans: SpanDetail[];
  /** Optional shadow results keyed by span_id. */
  shadowResults?: Map<string, ShadowResult[]>;
}

/**
 * Reconstructs the LLM conversation from span data and renders it
 * as a chat interface with role-based message bubbles.
 * When shadowResults are provided, renders ShadowChatResponse components
 * after assistant messages that have corresponding shadow results.
 */
function ChatView({ spans, shadowResults }: ChatViewProps) {
  const { messages, messageSpanMap } = useMemo(
    () => extractConversation(spans),
    [spans],
  );

  if (messages.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-2">
        <Settings className="size-8 opacity-40" />
        <p className="text-sm">No chat messages found in trace spans.</p>
        <p className="text-xs">
          Chat view requires spans with OpenAI-format request/response bodies.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 py-4 overflow-y-auto">
      {messages.map((msg, i) => {
        const key = `${msg.role}-${i}`;
        const elements: React.ReactNode[] = [];

        if (msg.role === "system") {
          elements.push(
            <SystemMessage key={key} content={msg.content ?? ""} />,
          );
          return elements;
        }

        if (msg.role === "user") {
          elements.push(<UserMessage key={key} content={msg.content ?? ""} />);
          return elements;
        }

        if (msg.role === "assistant") {
          if (msg.tool_calls && msg.tool_calls.length > 0) {
            elements.push(
              <AssistantToolCallMessage
                key={key}
                content={msg.content}
                toolCalls={msg.tool_calls}
              />,
            );
          } else {
            const hasContent = msg.content && msg.content.trim().length > 0;
            if (!hasContent) {
              // Fallback: empty assistant with no enriched tool_calls
              const next = messages[i + 1];
              if (next && (next.role === "function" || next.role === "tool")) {
                elements.push(
                  <FunctionCallIndicator
                    key={key}
                    functionName={next.name ?? "function"}
                  />,
                );
              }
            } else {
              elements.push(
                <AssistantMessage key={key} content={msg.content ?? ""} />,
              );
            }
          }

          // Render shadow results for this assistant message if available
          if (shadowResults) {
            const sourceSpan = messageSpanMap.get(i);
            if (sourceSpan) {
              const results = shadowResults.get(sourceSpan.spanId);
              if (results && results.length > 0) {
                for (const result of results) {
                  elements.push(
                    <ShadowChatResponse
                      key={`shadow-${result.id}`}
                      result={result}
                      originalResponse={msg.content}
                      originalLatencyMs={
                        sourceSpan.durationMicros
                          ? Math.round(sourceSpan.durationMicros / 1000)
                          : null
                      }
                    />,
                  );
                }
              }
            }
          }

          return elements.length > 0 ? elements : null;
        }

        if (msg.role === "tool" || msg.role === "function") {
          return (
            <ToolMessage
              key={key}
              name={msg.name}
              content={msg.content ?? ""}
            />
          );
        }

        return null;
      })}
    </div>
  );
}

export default ChatView;
