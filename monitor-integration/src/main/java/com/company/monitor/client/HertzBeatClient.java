package com.company.monitor.client;

import com.company.monitor.common.BizException;
import com.company.monitor.config.HertzBeatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HertzBeat REST API 客户端。
 *
 * <p>说明：HertzBeat 不同版本的接口路径/字段可能存在差异，本客户端实现以常见版本为准，
 * 接入前请对照部署版本的 Swagger/Knife4j 文档（通常在 /swagger-ui 或 /doc.html）核对。
 */
@Slf4j
@Component
public class HertzBeatClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final HertzBeatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存的访问令牌；失效时重新登录。 */
    private final AtomicReference<String> tokenRef = new AtomicReference<>();

    public HertzBeatClient(OkHttpClient httpClient, HertzBeatProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * 登录并缓存 token。
     */
    public synchronized String login() {
        String url = base() + "/api/account/auth/form";
        try {
            String body = objectMapper.createObjectNode()
                    .put("identifier", properties.getUsername())
                    .put("credential", properties.getPassword())
                    .toString();
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BizException("HertzBeat 登录失败, http=" + response.code());
                }
                JsonNode root = objectMapper.readTree(respBody);
                JsonNode token = root.path("data").path("token");
                if (token.isMissingNode() || token.asText().isEmpty()) {
                    throw new BizException("HertzBeat 登录响应缺少 token: " + respBody);
                }
                tokenRef.set(token.asText());
                log.info("HertzBeat 登录成功");
                return tokenRef.get();
            }
        } catch (IOException e) {
            throw new BizException("HertzBeat 登录请求异常: " + e.getMessage());
        }
    }

    private String token() {
        String t = tokenRef.get();
        return t != null ? t : login();
    }

    /**
     * 查询监控项列表（原样返回 HertzBeat 响应体）。
     *
     * @param query 可选的查询关键字
     */
    public JsonNode listMonitors(String query) {
        StringBuilder url = new StringBuilder(base()).append("/api/monitors?pageIndex=0&pageSize=20");
        if (query != null && !query.isBlank()) {
            url.append("&search=").append(query);
        }
        return authedGet(url.toString());
    }

    private JsonNode authedGet(String url) {
        try {
            JsonNode result = doGet(url, token());
            if (result == null) {
                // token 可能失效，重新登录后重试一次
                login();
                result = doGet(url, token());
            }
            return result;
        } catch (IOException e) {
            throw new BizException("调用 HertzBeat 接口异常: " + e.getMessage());
        }
    }

    /**
     * @return null 表示鉴权失效（需重登），否则返回响应体
     */
    private JsonNode doGet(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                return null;
            }
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new BizException("HertzBeat 接口返回错误, http=" + response.code() + ", body=" + respBody);
            }
            return objectMapper.readTree(respBody);
        }
    }

    private String base() {
        String b = properties.getBaseUrl();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
