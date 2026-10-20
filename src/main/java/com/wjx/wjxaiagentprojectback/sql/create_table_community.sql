-- ============================================================
-- 社区交流模块建表脚本
--
-- 执行方式（Windows 下务必带 utf8mb4，否则默认标签会按 GBK 解析成乱码）：
--   mysql -uroot -p123456 --default-character-set=utf8mb4 wjx_ai_code_project \
--         < create_table_community.sql
--
-- 参考 F:\Blog-project\blog-platform\blog-server 的 article/tag/
-- article_tag/comment 表结构改写：
--   1. 表名加 community_ 前缀，避免与已有 app/chat_history/user 冲突
--   2. 主键改为雪花 ID（bigint 非自增），与项目其余表保持一致，
--      前端统一按字符串处理，禁止 Number() 转换
--   3. 评论/点赞由「游客昵称 + IP」改为「登录用户 authorId」，
--      并额外快照昵称/头像，避免用户改名后历史帖子展示不一致
--   4. 点赞由博客的 Redis Set 改为持久化点赞表（唯一索引防重复点赞），
--      让点赞数、点赞状态、取消点赞在数据库层保持强一致
-- ============================================================


CREATE TABLE IF NOT EXISTS `community_post`
(
    `id`           BIGINT       NOT NULL COMMENT '帖子 ID（雪花算法）',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题',
    `content`      LONGTEXT     NOT NULL COMMENT '正文（纯文本/Markdown 源码）',
    `excerpt`      VARCHAR(500)          DEFAULT NULL COMMENT '摘要（为空时自动截取正文）',
    `cover`        VARCHAR(500)          DEFAULT NULL COMMENT '封面图地址',
    `appId`        BIGINT                DEFAULT NULL COMMENT '关联的 AI 生成应用 ID（分享作品）',
    `authorId`     BIGINT       NOT NULL COMMENT '作者用户 ID',
    `authorName`   VARCHAR(50)           DEFAULT NULL COMMENT '作者昵称快照',
    `authorAvatar` VARCHAR(1024)         DEFAULT NULL COMMENT '作者头像快照',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '0 草稿 1 已发布',
    `viewCount`    INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
    `likeCount`    INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    `commentCount` INT          NOT NULL DEFAULT 0 COMMENT '评论数',
    `createTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status_createTime` (`status`, `createTime`),
    KEY `idx_authorId` (`authorId`),
    KEY `idx_appId` (`appId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区帖子';

CREATE TABLE IF NOT EXISTS `community_tag`
(
    `id`         BIGINT      NOT NULL COMMENT '标签 ID（雪花算法）',
    `name`       VARCHAR(50) NOT NULL COMMENT '标签名',
    `createTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区标签';

CREATE TABLE IF NOT EXISTS `community_post_tag`
(
    `postId` BIGINT NOT NULL COMMENT '帖子 ID',
    `tagId`  BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (`postId`, `tagId`),
    KEY `idx_tagId` (`tagId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子-标签关联';

CREATE TABLE IF NOT EXISTS `community_comment`
(
    `id`         BIGINT      NOT NULL COMMENT '评论 ID（雪花算法）',
    `postId`     BIGINT      NOT NULL COMMENT '帖子 ID',
    `parentId`   BIGINT               DEFAULT NULL COMMENT '父评论 ID（为空表示一级评论）',
    `authorId`   BIGINT      NOT NULL COMMENT '评论者用户 ID',
    `nickname`   VARCHAR(50) NOT NULL COMMENT '评论者昵称快照',
    `avatar`     VARCHAR(1024)        DEFAULT NULL COMMENT '评论者头像快照',
    `content`    VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `ip`         VARCHAR(50)          DEFAULT NULL COMMENT '评论者 IP',
    `userAgent`  VARCHAR(500)         DEFAULT NULL COMMENT '浏览器 UA',
    `createTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `isDelete`   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_postId` (`postId`),
    KEY `idx_parentId` (`parentId`),
    KEY `idx_authorId` (`authorId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区评论';

CREATE TABLE IF NOT EXISTS `community_post_like`
(
    `id`         BIGINT   NOT NULL COMMENT '点赞记录 ID（雪花算法）',
    `postId`     BIGINT   NOT NULL COMMENT '帖子 ID',
    `userId`     BIGINT   NOT NULL COMMENT '点赞用户 ID',
    `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`postId`, `userId`),
    KEY `idx_userId` (`userId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区帖子点赞记录';

-- 默认标签，方便社区初次使用就有话题可筛
INSERT IGNORE INTO `community_tag` (`id`, `name`)
VALUES (1, '作品分享'),
       (2, 'AI 开发'),
       (3, '经验交流'),
       (4, '求助提问'),
       (5, '前端'),
       (6, '后端');
