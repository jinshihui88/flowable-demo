-- 首次部署时以 DBA 账号执行：创建 Flowable 示例库。
-- 业务表 leave_request 由应用启动时通过 schema.sql 自动创建（CREATE TABLE IF NOT EXISTS）；
-- ACT_* 引擎表由 Flowable 的 database-schema-update=true 自动创建。
CREATE DATABASE IF NOT EXISTS flowable_demo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 可选：为应用建立最小权限账号（生产推荐，替换掉默认口令后再使用）。
-- 使用本账号时，通过环境变量覆盖连接信息：
--   DB_USER=flowable_app  DB_PASSWORD=<你的口令>
CREATE USER IF NOT EXISTS 'flowable_app'@'localhost' IDENTIFIED BY 'ChangeThis_StrongPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP
    ON flowable_demo.* TO 'flowable_app'@'localhost';
FLUSH PRIVILEGES;
