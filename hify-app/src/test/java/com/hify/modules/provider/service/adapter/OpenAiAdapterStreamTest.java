package com.hify.modules.provider.service.adapter;

import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatMessage;
import com.hify.modules.provider.client.LlmHttpClient;
import com.hify.modules.provider.entity.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiAdapterStreamTest {

    private LlmHttpClient httpClient;
    private OpenAiAdapter adapter;
    private Provider provider;

    @BeforeEach
    void setUp() {
        httpClient = mock(LlmHttpClient.class);
        adapter = new OpenAiAdapter(httpClient);
        provider = new Provider();
        provider.setBaseUrl("https://api.openai.com");
        provider.setApiKey("sk-test");
    }

    @Test
    @DisplayName("P0-2: streamChat — 正常 SSE 流逐字解析并触发 onDelta")
    void streamChat_normalFlow_shouldTriggerDeltaForEachToken() throws IOException {
        List<String> deltas = new ArrayList<>();
        List<String> finishes = new ArrayList<>();

        doAnswer(inv -> {
            Consumer<String> onLine = inv.getArgument(3);
            onLine.accept("data: {\"choices\":[{\"delta\":{\"content\":\"你\"},\"finish_reason\":null}]}");
            onLine.accept("data: {\"choices\":[{\"delta\":{\"content\":\"好\"},\"finish_reason\":null}]}");
            onLine.accept("data: {\"choices\":[{\"delta\":{\"content\":\"！\"},\"finish_reason\":\"stop\"}]}");
            onLine.accept("data: [DONE]");
            return null;
        }).when(httpClient).postStream(anyString(), any(), anyString(), any());

        ChatRequest request = new ChatRequest();
        request.setModel("gpt-4");
        request.setMessages(List.of(new ChatMessage("user", "Hi")));

        adapter.streamChat(provider, request, deltas::add, finishes::add);

        assertEquals(List.of("你", "好", "！"), deltas);
        assertTrue(finishes.contains("stop"));
    }

    @Test
    @DisplayName("P0-2: streamChat — JSON 解析异常不中断流，继续消费后续行")
    void streamChat_malformedJson_shouldContinueStreaming() throws IOException {
        List<String> deltas = new ArrayList<>();
        List<String> finishes = new ArrayList<>();

        doAnswer(inv -> {
            Consumer<String> onLine = inv.getArgument(3);
            onLine.accept("data: {broken json");
            onLine.accept("data: {\"choices\":[{\"delta\":{\"content\":\"继续\"},\"finish_reason\":null}]}");
            return null;
        }).when(httpClient).postStream(anyString(), any(), anyString(), any());

        ChatRequest request = new ChatRequest();
        request.setModel("gpt-4");
        request.setMessages(List.of(new ChatMessage("user", "Hi")));

        adapter.streamChat(provider, request, deltas::add, finishes::add);

        assertEquals(List.of("继续"), deltas);
    }

    @Test
    @DisplayName("P0-2: streamChat — 空 delta 不触发 onDelta")
    void streamChat_emptyDelta_shouldNotTriggerDelta() throws IOException {
        List<String> deltas = new ArrayList<>();
        List<String> finishes = new ArrayList<>();

        doAnswer(inv -> {
            Consumer<String> onLine = inv.getArgument(3);
            onLine.accept("data: {\"choices\":[{\"delta\":{},\"finish_reason\":null}]}");
            onLine.accept("data: {\"choices\":[{\"delta\":{\"content\":\"有\"},\"finish_reason\":\"stop\"}]}");
            return null;
        }).when(httpClient).postStream(anyString(), any(), anyString(), any());

        ChatRequest request = new ChatRequest();
        request.setModel("gpt-4");
        request.setMessages(List.of(new ChatMessage("user", "Hi")));

        adapter.streamChat(provider, request, deltas::add, finishes::add);

        assertEquals(List.of("有"), deltas);
        assertTrue(finishes.contains("stop"));
    }

    @Test
    @DisplayName("P0-2: chat — 同步调用解析完整响应")
    void chat_sync_shouldParseResponse() throws IOException {
        when(httpClient.post(anyString(), any(), anyString())).thenReturn("""
                {
                    "id": "chatcmpl-123",
                    "model": "gpt-4",
                    "choices": [
                        {
                            "finish_reason": "stop",
                            "message": { "role": "assistant", "content": "Hello!" }
                        }
                    ],
                    "usage": { "prompt_tokens": 5, "completion_tokens": 2, "total_tokens": 7 }
                }
                """);

        ChatRequest request = new ChatRequest();
        request.setModel("gpt-4");
        request.setMessages(List.of(new ChatMessage("user", "Hi")));

        var response = adapter.chat(provider, request);

        assertEquals("Hello!", response.getContent());
        assertEquals("stop", response.getFinishReason());
        assertEquals("gpt-4", response.getModel());
        assertNotNull(response.getUsage());
        assertEquals(7, response.getUsage().getTotalTokens());
    }
}
