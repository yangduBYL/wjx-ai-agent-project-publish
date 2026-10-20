-- ============================================================
-- 应用 / 对话历史 建表脚本
--
-- 说明：这两张表此前只在本地库里手工建过，仓库里没有脚本，
--       导致别人 clone 项目后无法直接跑起来。这里从本地库反推补齐。
--
-- 执行方式（Windows 下务必带 utf8mb4）：
--   mysql -uroot -p123456 --default-character-set=utf8mb4 wjx_ai_code_project \
--         < create_table_app_chat_history.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `app`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `appName`      VARCHAR(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '应用名称',
    `cover`        VARCHAR(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '应用封面',
    `initprompt`   TEXT COLLATE utf8mb4_unicode_ci COMMENT '应用初始化提示',
    `codeGenType`  VARCHAR(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '代码生成类型 html/multi_file/vue_project',
    `deployKey`    VARCHAR(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '部署标识',
    `deployedTime` DATETIME                                DEFAULT NULL COMMENT '部署时间',
    `priority`     INT          NOT NULL DEFAULT 0 COMMENT '优先级（>=99 为精选）',
    `userId`       BIGINT       NOT NULL COMMENT '用户 id',
    `editTime`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    `createTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unk_deployKey` (`deployKey`),
    KEY `idx_appName` (`appName`),
    KEY `idx_userId` (`userId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='应用';

CREATE TABLE IF NOT EXISTS `chat_history`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `message`     TEXT COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息',
    `messageType` VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'user/ai',
    `appId`       BIGINT      NOT NULL COMMENT '应用 id',
    `userId`      BIGINT      NOT NULL COMMENT '创建用户 id',
    `createTime`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否删除',
    `parentId`    BIGINT               DEFAULT NULL COMMENT '父消息 id（用于上下文关联）',
    PRIMARY KEY (`id`),
    KEY `idx_appId` (`appId`),
    KEY `idx_createTime` (`createTime`),
    KEY `idx_appId_createTime` (`appId`, `createTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对话历史';
