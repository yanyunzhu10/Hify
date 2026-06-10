package com.hify.modules.knowledge.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * pgvector 数据源（PostgreSQL，仅用于向量写入与相似度检索）。
 * <p>
 * 不暴露 DataSource / DataSourceProperties bean，只暴露 JdbcTemplate 和
 * NamedParameterJdbcTemplate，避免与 Spring Boot 自动配置的 MySQL 主数据源竞争。
 * </p>
 */
@Configuration
public class PgVectorConfig {

    @Value("${spring.datasource.pgvector.url}")
    private String url;

    @Value("${spring.datasource.pgvector.username}")
    private String username;

    @Value("${spring.datasource.pgvector.password:}")
    private String password;

    private DataSource buildPgDs() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean("pgvectorJdbcTemplate")
    public JdbcTemplate pgvectorJdbcTemplate() {
        JdbcTemplate t = new JdbcTemplate(buildPgDs());
        t.setQueryTimeout(30);
        return t;
    }

    @Bean("pgvectorNamedJdbcTemplate")
    public NamedParameterJdbcTemplate pgvectorNamedJdbcTemplate() {
        return new NamedParameterJdbcTemplate(buildPgDs());
    }
}
