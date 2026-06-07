-- ============================================================
-- Migration: bool → int2 统一迁移
--
-- 背景：hify.sql 中 deleted/enabled 列已从 bool 统一改为 int2，
-- 但已有数据库中的列仍是 bool 类型，导致 MyBatis-Plus 发送
-- WHERE deleted=0 时 PG 报 "operator does not exist: boolean = integer"。
--
-- 执行方式：
--   psql -U postgres -d hify -f deploy/sql/migrate_bool_to_int2.sql
--
-- 注意：如果 t_provider_health 等表从未有过 deleted/enabled 列，对应语句会报错（可忽略）
-- ============================================================

BEGIN;

-- ===== t_agent =====
ALTER TABLE t_agent ALTER COLUMN enabled TYPE int2 USING (enabled::int)::int2;
-- (deleted 已在 hify.sql 中改为 int2，若库中已改则跳过)

-- ===== t_chat_message =====
DROP INDEX IF EXISTS idx_chat_message_session_deleted_created;
ALTER TABLE t_chat_message ALTER COLUMN deleted TYPE int2 USING (deleted::int)::int2;
ALTER TABLE t_chat_message ALTER COLUMN deleted SET DEFAULT 0;
CREATE INDEX idx_chat_message_session_deleted_created ON t_chat_message (session_id, deleted, created_at);

-- ===== t_chat_session =====
DROP INDEX IF EXISTS idx_chat_session_agent_deleted_updated;
ALTER TABLE t_chat_session ALTER COLUMN deleted TYPE int2 USING (deleted::int)::int2;
ALTER TABLE t_chat_session ALTER COLUMN deleted SET DEFAULT 0;
CREATE INDEX idx_chat_session_agent_deleted_updated ON t_chat_session (agent_id, deleted, updated_at);

-- ===== t_demo_item =====
ALTER TABLE t_demo_item ALTER COLUMN deleted TYPE int2 USING (deleted::int)::int2;
ALTER TABLE t_demo_item ALTER COLUMN deleted SET DEFAULT 0;

-- ===== t_mcp_server =====
DROP INDEX IF EXISTS idx_mcp_server_type_deleted;
ALTER TABLE t_mcp_server ALTER COLUMN enabled TYPE int2 USING (enabled::int)::int2;
ALTER TABLE t_mcp_server ALTER COLUMN enabled SET DEFAULT 1;
ALTER TABLE t_mcp_server ALTER COLUMN deleted TYPE int2 USING (deleted::int)::int2;
ALTER TABLE t_mcp_server ALTER COLUMN deleted SET DEFAULT 0;
CREATE INDEX idx_mcp_server_type_deleted ON t_mcp_server (type, deleted);

-- ===== t_model_config =====
ALTER TABLE t_model_config ALTER COLUMN enabled TYPE int2 USING (enabled::int)::int2;
ALTER TABLE t_model_config ALTER COLUMN enabled SET DEFAULT 1;
ALTER TABLE t_model_config ALTER COLUMN deleted TYPE int2 USING (deleted::int)::int2;
ALTER TABLE t_model_config ALTER COLUMN deleted SET DEFAULT 0;

-- ===== t_provider =====
ALTER TABLE t_provider ALTER COLUMN enabled TYPE int2 USING (enabled::int)::int2;
ALTER TABLE t_provider ALTER COLUMN enabled SET DEFAULT 1;
-- (deleted 已是 int2 则跳过)

COMMIT;
