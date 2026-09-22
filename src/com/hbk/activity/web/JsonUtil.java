package com.hbk.activity.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 工具类：封装 Gson，供 Web 接口层做对象与 JSON 的相互转换。
 *
 * <p>【为什么需要它】
 * HTTP 接口收发的都是 JSON 文本，Java 里是对象，两边要互相转换：
 * <pre>
 *   请求：JSON 文本 → Java 对象（Gson.fromJson）
 *   响应：Java 对象 → JSON 文本（Gson.toJson）
 * </pre>
 *
 * <p>【为什么用 Gson 而不是手写解析】
 * 手写 JSON 解析要处理字符串转义、Unicode、数字、null 等大量细节，容易出错。
 * Gson 是一个不到 300KB 的独立 jar（放在 lib/ 下），没有其它依赖，
 * 用最少代码换来可靠的解析能力。
 *
 * <p>【LocalDateTime 适配器解决什么问题】
 * 实体类里的 startTime / endTime 是 LocalDateTime，Gson 默认会把它拆成
 * {year:2026, month:12, dayOfMonth:20, ...} 这种对象，前端没法直接用。
 * 因此这里注册一个适配器，把它统一成字符串 "yyyy-MM-dd HH:mm:ss"：
 * <pre>
 *   写出去：LocalDateTime → "2026-12-20 09:00:00"
 *   读进来："2026-12-20 09:00:00" → LocalDateTime
 * </pre>
 * 这个格式与前端 el-date-picker 的 value-format 完全一致。
 */
public final class JsonUtil {

    /** 接口统一使用的时间格式 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 全局共享的 Gson 实例。
     * Gson 是线程安全的，构造一次反复使用即可。
     */
    private static final Gson GSON = new GsonBuilder()
            // 用同一个 TypeAdapter 同时处理"读"和"写"，
            // 分两次 registerTypeAdapter 会让后注册的那次覆盖前一次
            .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.format(DATE_TIME_FORMATTER));
                    }
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalDateTime.parse(in.nextString(), DATE_TIME_FORMATTER);
                }
            })
            // 值为 null 的字段也输出，方便前端判断字段是否存在
            .serializeNulls()
            .create();

    private JsonUtil() {
        // 工具类禁止实例化
    }

    /**
     * 把 Java 对象转换成 JSON 字符串。
     *
     * @param obj 待转换对象
     * @return JSON 文本
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * 把 JSON 字符串解析成 JsonObject（可按键取值，适合读取请求体里的字段）。
     *
     * @param json JSON 文本
     * @return JsonObject；文本为空时返回空对象
     */
    public static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * 把 JSON 字符串转换成指定类型的对象。
     *
     * @param json  JSON 文本
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 转换后的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }
}
