package com.hify.common.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * PostgreSQL {@code jsonb} 列专用 TypeHandler。
 * <p>
 * MyBatis-Plus 自带的 {@code JacksonTypeHandler} 写入时用
 * {@link PreparedStatement#setString}，PG 驱动将其当作 {@code varchar}
 * 发送，遇到 {@code jsonb} 列会报
 * "expression is of type character varying"。
 * <p>
 * 本实现用 {@link PreparedStatement#setObject(int, Object, int)}
 * 传 {@link Types#OTHER}，PG 驱动据此推断目标类型为 {@code jsonb}，
 * 列类型完全匹配。无 PG 类依赖，common 模块可直接用。
 * <p>
 * 用法：Entity 字段上加 {@code @TableField(typeHandler = PgJsonbTypeHandler.class)}。
 * </p>
 */
@MappedTypes({Map.class, Object.class})
public class PgJsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        String json = toJson(parameter);
        ps.setObject(i, json, Types.OTHER);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromJson(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromJson(cs.getString(columnIndex));
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("jsonb 序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }
}
