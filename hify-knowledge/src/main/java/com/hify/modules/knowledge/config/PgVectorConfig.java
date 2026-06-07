package com.hify.modules.knowledge.config;

import com.pgvector.PGvector;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * pgvector 数据源（PostgreSQL，仅用于向量写入与相似度检索）。
 * <p>
 * 与 MySQL 主库完全独立：Spring Boot 自动配置的 DataSource / MyBatis-Plus 走 MySQL，
 * 本配置创建独立的 DataSource + JdbcTemplate，专供 {@code ChunkRepository} 使用。
 * </p>
 */
@Configuration
public class PgVectorConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.pgvector")
    public DataSourceProperties pgvectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource pgvectorDataSource() {
        return pgvectorDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public JdbcTemplate pgvectorJdbcTemplate(DataSource pgvectorDataSource) {
        JdbcTemplate t = new JdbcTemplate(pgvectorDataSource);
        // 每次查询前确保 PG 驱动认识 vector 类型（连接池复用时只需注册一次，但无副作用）
        t.setQueryTimeout(30);
        return t;
    }

    @Bean
    public NamedParameterJdbcTemplate pgvectorNamedJdbcTemplate(DataSource pgvectorDataSource) {
        return new NamedParameterJdbcTemplate(pgvectorDataSource);
    }
}
