-- ============================================================
-- Nâng cấp DB tạo từ bản schema đầu tiên (19 bảng) lên bản hiện tại (23 bảng).
-- Chỉ THÊM cột / bảng, không xóa dữ liệu. Chạy một lần.
-- DB mới tạo bằng 01_schema.sql thì KHÔNG cần chạy file này.
-- ============================================================
USE movie_web;

ALTER TABLE movies
    ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Admin ẩn/hiện phim' AFTER age_rating;

-- ============================================================
-- F. THÔNG BÁO & THỐNG KÊ
-- ============================================================

-- dedupe_key chống tạo trùng khi consumer chạy lại,
-- ví dụ 'REVIEW_LIKE:12:34' (review 12, người like 34).
CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id     BIGINT NOT NULL COMMENT 'Người nhận',
    type        ENUM('REVIEW_COMMENT', 'COMMENT_REPLY', 'REVIEW_LIKE', 'MOVIE_RELEASED') NOT NULL,
    actor_id    BIGINT NULL COMMENT 'Người gây ra thông báo, NULL nếu do hệ thống',

    movie_id    BIGINT NULL,
    review_id   BIGINT NULL,
    comment_id  BIGINT NULL,

    message     VARCHAR(500) NOT NULL,
    dedupe_key  VARCHAR(100) NOT NULL,
    read_at     DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_notifications_dedupe (user_id, dedupe_key),
    INDEX idx_notifications_user_time (user_id, created_at),
    INDEX idx_notifications_user_unread (user_id, read_at),

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE
);

-- Job gom lượt xem từ Redis ghi xuống đây, phục vụ dashboard và "hot theo tuần/tháng".
CREATE TABLE IF NOT EXISTS movie_view_daily (
    movie_id    BIGINT NOT NULL,
    view_date   DATE NOT NULL,
    view_count  BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (movie_id, view_date),
    INDEX idx_view_daily_date (view_date, view_count),

    CONSTRAINT fk_view_daily_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT chk_view_daily_count CHECK (view_count >= 0)
);


-- ============================================================
-- G. HỆ THỐNG
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,

    actor_id     BIGINT NULL,
    action       VARCHAR(50) NOT NULL COMMENT 'Ví dụ MOVIE_UPDATE, USER_BAN, REVIEW_HIDE',
    target_type  VARCHAR(50) NOT NULL,
    target_id    BIGINT NULL,
    before_data  JSON NULL,
    after_data   JSON NULL,
    ip_address   VARCHAR(45) NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_actor_time (actor_id, created_at),
    INDEX idx_audit_action_time (action, created_at),
    INDEX idx_audit_target (target_type, target_id),
    INDEX idx_audit_time (created_at),

    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Transactional outbox: ghi event cùng transaction với dữ liệu,
-- job đọc các dòng sent_at IS NULL rồi gửi sang RabbitMQ.
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    event_type      VARCHAR(100) NOT NULL COMMENT 'Ví dụ movie.released, comment.created',
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    payload         JSON NOT NULL,

    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         DATETIME NULL,

    INDEX idx_outbox_pending (sent_at, id)
);
