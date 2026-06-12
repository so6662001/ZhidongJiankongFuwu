package com.company.monitor.notify;

import com.company.monitor.common.BizException;
import com.company.monitor.config.WeComProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信自建应用客户端：获取 access_token（Redis 缓存，失败回退内存）并发送 textcard 应用消息。
 */
@Slf4j
@Component
public class WeComAppClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";

    private final OkHttpClient httpClient;
    private final WeComProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String memToken;
    private volatile long memTokenExpireAt;

    public WeComAppClient(OkHttpClient httpClient, WeComProperties properties,
                          StringRedisTemplate redisTemplate) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 发送 textcard 消息到指定 userid。返回是否成功（失败抛 BizException）。
     */
    public void sendTextCard(List<String> userIds, String title, String description, String url) {
        if (!properties.isConfigured()) {
            throw new BizException("企业微信应用未配置(corpid/agentid/secret)");
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException("无接收人 userid");
        }
        String token = getAccessToken(false);
        JsonNode resp = doSend(token, userIds, title, description, url);
        int errcode = resp.path("errcode").asInt(-1);
        if (errcode == 42001 || errcode == 40014) {
            // token 失效，刷新重试一次
            token = getAccessToken(true);
            resp = doSend(token, userIds, title, description, url);
            errcode = resp.path("errcode").asInt(-1);
        }
        if (errcode != 0) {
            throw new BizException("企业微信发送失败: " + resp.toString());
        }
    }

    private JsonNode doSend(String token, List<String> userIds, String title, String description, String url) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("touser", String.join("|", userIds));
            root.put("msgtype", "textcard");
            root.put("agentid", Integer.parseInt(properties.getAgentid()));
            ObjectNode card = objectMapper.createObjectNode();
            card.put("title", title);
            card.put("description", description);
            card.put("url", url != null ? url : "");
            card.put("btntxt", "查看详情");
            root.set("textcard", card);

            Request request = new Request.Builder()
                    .url(SEND_URL + "?access_token=" + token)
                    .post(RequestBody.create(root.toString(), JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                return objectMapper.readTree(body);
            }
        } catch (IOException e) {
            throw new BizException("企业微信发送异常: " + e.getMessage());
        }
    }

    private String getAccessToken(boolean forceRefresh) {
        String cacheKey = "wecom:token:" + properties.getAgentid();
        if (!forceRefresh) {
            String cached = readCache(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        synchronized (this) {
            if (!forceRefresh) {
                String cached = readCache(cacheKey);
                if (cached != null) {
                    return cached;
                }
            }
            String token = fetchToken();
            // 提前 200s 过期
            writeCache(cacheKey, token, 7000);
            return token;
        }
    }

    private String fetchToken() {
        try {
            HttpUrl url = HttpUrl.parse(TOKEN_URL).newBuilder()
                    .addQueryParameter("corpid", properties.getCorpid())
                    .addQueryParameter("corpsecret", properties.getSecret())
                    .build();
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readTree(body);
                if (root.path("errcode").asInt(0) != 0) {
                    throw new BizException("获取企业微信 access_token 失败: " + body);
                }
                return root.path("access_token").asText();
            }
        } catch (IOException e) {
            throw new BizException("获取企业微信 access_token 异常: " + e.getMessage());
        }
    }

    private String readCache(String key) {
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v != null) {
                return v;
            }
        } catch (Exception e) {
            log.debug("Redis 读取 token 失败，回退内存缓存: {}", e.getMessage());
        }
        if (memToken != null && System.currentTimeMillis() < memTokenExpireAt) {
            return memToken;
        }
        return null;
    }

    private void writeCache(String key, String token, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, token, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入 token 失败，使用内存缓存: {}", e.getMessage());
        }
        this.memToken = token;
        this.memTokenExpireAt = System.currentTimeMillis() + ttlSeconds * 1000;
    }
}
