package com.company.monitor.notify;

import com.company.monitor.common.BizException;
import com.company.monitor.config.WeComProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 企业微信群机器人：向群发送 markdown 消息（最易配置的真实投递渠道；群播无法精准@到 userid，
 * 可通过 mentioned_mobile_list 用手机号 @ 人）。
 */
@Component
public class WeComRobotClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final WeComProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeComRobotClient(OkHttpClient httpClient, WeComProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    public void sendMarkdown(String markdown, List<String> mentionMobiles) {
        if (!properties.isRobotConfigured()) {
            throw new BizException("企业微信群机器人未配置(robot-webhook)");
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("msgtype", "markdown");
            ObjectNode md = objectMapper.createObjectNode();
            md.put("content", markdown);
            root.set("markdown", md);
            Request request = new Request.Builder()
                    .url(properties.getRobotWebhook())
                    .post(RequestBody.create(root.toString(), JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                JsonNode resp = objectMapper.readTree(body);
                if (resp.path("errcode").asInt(-1) != 0) {
                    throw new BizException("企业微信群机器人发送失败: " + body);
                }
            }
        } catch (IOException e) {
            throw new BizException("企业微信群机器人发送异常: " + e.getMessage());
        }
    }
}
