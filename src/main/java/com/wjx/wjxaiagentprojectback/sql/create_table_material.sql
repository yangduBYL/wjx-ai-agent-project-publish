-- ============================================================
-- 素材库建表脚本
--
-- 执行方式（Windows 下务必带 utf8mb4，否则中文注释/文件名会乱码）：
--   mysql -uroot -p123456 --default-character-set=utf8mb4 wjx_ai_code_project \
--         < create_table_material.sql
--
-- 参考 F:\Blog-project\blog-platform\blog-server 的 media 表改写：
--   1. 主键改为雪花 ID（与项目其余表一致），去掉自增
--   2. 新增 userId：素材按用户隔离（博客是全站共用，这里每个用户只看到自己的）
--   3. 新增 contentType / storagePath：本地兜底存储时用于定位文件与删除清理
--   4. 新增 isDelete：逻辑删除，与项目其它表保持一致
-- ============================================================

CREATE TABLE IF NOT EXISTS `material`
(
    `id`           BIGINT        NOT NULL COMMENT '素材 ID（雪花算法）',
    `userId`       BIGINT        NOT NULL COMMENT '上传用户 ID',
    `url`          VARCHAR(1024) NOT NULL COMMENT '访问地址（COS 公网地址或本地 /api/material/file/...）',
    `storagePath`  VARCHAR(512)           DEFAULT NULL COMMENT '存储定位：COS 对象键或本地相对路径（删除时清理）',
    `originalName` VARCHAR(255)           DEFAULT NULL COMMENT '原始文件名',
    `contentType`  VARCHAR(100)           DEFAULT NULL COMMENT 'MIME 类型',
    `size`         BIGINT                 DEFAULT NULL COMMENT '文件大小（字节）',
    `createTime`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `isDelete`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_userId_createTime` (`userId`, `createTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='素材库（用户上传的图片）';
