-- ============================================================
-- MOVIE REVIEW WEBSITE - SCHEMA
-- Yêu cầu: MySQL 8.0.16+ (để CHECK constraint có hiệu lực)
-- Quy ước:
--   * Thời gian lưu theo UTC (hibernate.jdbc.time_zone=UTC)
--   * Cột đếm/điểm trung bình (avg_rating, rating_count, ...) do tầng
--     service cập nhật trong cùng transaction với thao tác gốc
--   * Cột *_url của ảnh nhận object key trên MinIO/S3 (ảnh upload)
--     hoặc URL đầy đủ (ảnh ở nơi khác, như TMDB)
-- ============================================================

CREATE DATABASE IF NOT EXISTS movie_web
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE movie_web;


-- ============================================================
-- A. TÀI KHOẢN & PHÂN QUYỀN
-- ============================================================

CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    username            VARCHAR(50)  NOT NULL,
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(255) NULL COMMENT 'BCrypt/Argon2. NULL nếu sau này đăng nhập qua OAuth',

    full_name           VARCHAR(100) NULL,
    avatar_url          VARCHAR(500) NULL,
    bio                 VARCHAR(500) NULL,

    status              ENUM('ACTIVE', 'INACTIVE', 'BANNED') NOT NULL DEFAULT 'ACTIVE',
    email_verified_at   DATETIME NULL,
    last_login_at       DATETIME NULL,

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME NULL COMMENT 'Xóa mềm: giữ review/rating của người dùng đã rời đi',

    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
);

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,

    UNIQUE KEY uk_roles_name (name)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Dùng chung cho refresh token, xác thực email và quên mật khẩu.
-- Chỉ lưu SHA-256 của token, không lưu token gốc.
CREATE TABLE auth_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id     BIGINT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    type        ENUM('REFRESH', 'EMAIL_VERIFY', 'PASSWORD_RESET') NOT NULL,

    expires_at  DATETIME NOT NULL,
    used_at     DATETIME NULL COMMENT 'Đã dùng hoặc đã thu hồi',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_auth_tokens_hash (token_hash),
    INDEX idx_auth_tokens_user_type (user_id, type),
    INDEX idx_auth_tokens_expires (expires_at),

    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


-- ============================================================
-- B. DANH MỤC
-- ============================================================

CREATE TABLE genres (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,

    UNIQUE KEY uk_genres_name (name),
    UNIQUE KEY uk_genres_slug (slug)
);

CREATE TABLE countries (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,

    code VARCHAR(2)   NOT NULL COMMENT 'ISO 3166-1 alpha-2',
    name VARCHAR(100) NOT NULL,

    UNIQUE KEY uk_countries_code (code),
    UNIQUE KEY uk_countries_name (name)
);

-- Gộp directors + actors: một người có thể vừa đạo diễn vừa đóng phim.
CREATE TABLE people (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(180) NOT NULL,
    biography   TEXT NULL,
    avatar_url  VARCHAR(500) NULL,
    birth_date  DATE NULL,
    tmdb_id     INT NULL COMMENT 'ID bên TMDB, dùng để import dữ liệu',

    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_people_slug (slug),
    UNIQUE KEY uk_people_tmdb (tmdb_id),
    INDEX idx_people_name (name)
);


-- ============================================================
-- C. PHIM
-- ============================================================

-- Phải tắt trước khi tạo FULLTEXT index: stopword mặc định có "a", "i", và ngram
-- bỏ mọi token chứa stopword, khiến tìm "Mai" (ma, ai) không ra kết quả.
SET SESSION innodb_ft_enable_stopword = OFF;

CREATE TABLE movies (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    title               VARCHAR(255) NOT NULL,
    original_title      VARCHAR(255) NULL,
    slug                VARCHAR(300) NOT NULL,

    description         TEXT NULL,
    poster_url          VARCHAR(500) NULL,
    banner_url          VARCHAR(500) NULL,
    trailer_url         VARCHAR(500) NULL,

    release_date        DATE NULL,
    duration_minutes    INT NULL,
    original_language   VARCHAR(10) NULL COMMENT 'Mã ISO 639-1, ví dụ vi, en, ko',

    type                ENUM('MOVIE', 'SERIES') NOT NULL DEFAULT 'MOVIE',
    status              ENUM('UPCOMING', 'ONGOING', 'COMPLETED') NOT NULL DEFAULT 'COMPLETED',
    age_rating          VARCHAR(10) NULL COMMENT 'Ví dụ P, K, T13, T16, T18',

    is_hidden           BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Admin ẩn/hiện phim',
    tmdb_id             INT NULL COMMENT 'ID bên TMDB, dùng để import dữ liệu',

    -- Cột tổng hợp: tránh AVG/COUNT trên mỗi lần tải danh sách phim
    avg_rating          DECIMAL(4,2) NOT NULL DEFAULT 0,
    rating_count        INT NOT NULL DEFAULT 0,
    review_count        INT NOT NULL DEFAULT 0,
    view_count          BIGINT NOT NULL DEFAULT 0,

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME NULL,

    UNIQUE KEY uk_movies_slug (slug),
    UNIQUE KEY uk_movies_tmdb (tmdb_id),

    INDEX idx_movies_release (release_date),
    INDEX idx_movies_type_rating (type, avg_rating),
    INDEX idx_movies_rating (avg_rating, rating_count),
    INDEX idx_movies_views (view_count),
    INDEX idx_movies_created (created_at),

    -- ngram: parser mặc định bỏ qua từ dưới 3 ký tự nên không tìm được chữ như "Bố"
    FULLTEXT INDEX ft_movies_title (title, original_title) WITH PARSER ngram,

    CONSTRAINT chk_movies_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT chk_movies_avg_rating CHECK (avg_rating BETWEEN 0 AND 10),
    CONSTRAINT chk_movies_counts CHECK (rating_count >= 0 AND review_count >= 0 AND view_count >= 0)
);

CREATE TABLE movie_genres (
    movie_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,

    PRIMARY KEY (movie_id, genre_id),

    CONSTRAINT fk_movie_genres_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_movie_genres_genre FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
);

CREATE TABLE movie_countries (
    movie_id   BIGINT NOT NULL,
    country_id BIGINT NOT NULL,

    PRIMARY KEY (movie_id, country_id),

    CONSTRAINT fk_movie_countries_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_movie_countries_country FOREIGN KEY (country_id) REFERENCES countries(id) ON DELETE CASCADE
);

-- Thay movies.director_id + movie_actors: hỗ trợ nhiều đạo diễn,
-- và một diễn viên đóng nhiều vai trong cùng một phim.
CREATE TABLE movie_credits (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    movie_id        BIGINT NOT NULL,
    person_id       BIGINT NOT NULL,

    role            ENUM('DIRECTOR', 'ACTOR', 'WRITER', 'PRODUCER') NOT NULL,
    character_name  VARCHAR(150) NOT NULL DEFAULT '' COMMENT 'Chỉ dùng cho ACTOR. NOT NULL để UNIQUE có tác dụng',
    display_order   INT NOT NULL DEFAULT 0,

    UNIQUE KEY uk_movie_credit (movie_id, person_id, role, character_name),
    INDEX idx_credits_movie_role (movie_id, role, display_order),
    INDEX idx_credits_person (person_id, role),

    CONSTRAINT fk_credits_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_credits_person FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE CASCADE
);

-- Chỉ lưu thông tin tập phim để hiển thị và bình luận, không lưu video.
CREATE TABLE episodes (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,

    movie_id          BIGINT NOT NULL,
    season_number     INT NOT NULL DEFAULT 1,
    episode_number    INT NOT NULL,

    title             VARCHAR(255) NULL,
    description       TEXT NULL,
    duration_minutes  INT NULL,
    air_date          DATE NULL,

    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_movie_episode (movie_id, season_number, episode_number),
    INDEX idx_episodes_air_date (air_date),

    CONSTRAINT fk_episodes_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT chk_episodes_numbers CHECK (season_number > 0 AND episode_number > 0),
    CONSTRAINT chk_episodes_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0)
);


-- ============================================================
-- D. ĐÁNH GIÁ & BÌNH LUẬN
-- Với users dùng RESTRICT: nội dung công khai không bị mất do xóa nhầm
-- user. Xóa tài khoản = xóa mềm + ẩn danh hóa thông tin cá nhân.
-- ============================================================

CREATE TABLE ratings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id     BIGINT NOT NULL,
    movie_id    BIGINT NOT NULL,
    score       TINYINT NOT NULL,

    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_movie_rating (user_id, movie_id),
    INDEX idx_ratings_movie_score (movie_id, score),

    CONSTRAINT fk_ratings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ratings_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT chk_rating_score CHECK (score BETWEEN 1 AND 10)
);

-- Bài review: mỗi người tối đa một bài cho một phim.
-- Điểm số nằm ở ratings để có thể chấm điểm mà không cần viết bài.
CREATE TABLE reviews (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id         BIGINT NOT NULL,
    movie_id        BIGINT NOT NULL,

    title           VARCHAR(255) NOT NULL,
    content         MEDIUMTEXT NOT NULL,
    has_spoiler     BOOLEAN NOT NULL DEFAULT FALSE,

    status          ENUM('VISIBLE', 'HIDDEN') NOT NULL DEFAULT 'VISIBLE',

    like_count      INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,

    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_movie_review (user_id, movie_id),
    INDEX idx_reviews_movie_time (movie_id, status, created_at),
    INDEX idx_reviews_movie_likes (movie_id, status, like_count),
    INDEX idx_reviews_user_time (user_id, created_at),

    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT chk_reviews_counts CHECK (like_count >= 0 AND comment_count >= 0)
);

CREATE TABLE review_likes (
    user_id     BIGINT NOT NULL,
    review_id   BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, review_id),

    CONSTRAINT fk_review_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_likes_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE
);

-- review_id NULL: bình luận trên trang phim. Có giá trị: bình luận dưới bài review.
-- Người dùng xóa bình luận thì chuyển status = DELETED để giữ cây trả lời.
CREATE TABLE comments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id     BIGINT NOT NULL,
    movie_id    BIGINT NOT NULL,
    review_id   BIGINT NULL,
    parent_id   BIGINT NULL,

    content     TEXT NOT NULL,
    status      ENUM('VISIBLE', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'VISIBLE',

    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_comments_movie_time (movie_id, review_id, created_at),
    INDEX idx_comments_review_time (review_id, created_at),
    INDEX idx_comments_parent_time (parent_id, created_at),
    INDEX idx_comments_user_time (user_id, created_at),

    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_comments_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
);

-- Không dùng FK cho target_id vì trỏ tới hai bảng khác nhau.
CREATE TABLE reports (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,

    reporter_id  BIGINT NOT NULL,
    target_type  ENUM('REVIEW', 'COMMENT') NOT NULL,
    target_id    BIGINT NOT NULL,

    reason       ENUM('SPAM', 'OFFENSIVE', 'SPOILER', 'OTHER') NOT NULL,
    note         VARCHAR(500) NULL,

    status       ENUM('PENDING', 'RESOLVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    handled_by   BIGINT NULL,
    handled_at   DATETIME NULL,

    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_report_once (reporter_id, target_type, target_id),
    INDEX idx_reports_status_time (status, created_at),
    INDEX idx_reports_target (target_type, target_id),

    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_handler FOREIGN KEY (handled_by) REFERENCES users(id) ON DELETE SET NULL
);


-- ============================================================
-- E. DANH SÁCH CÁ NHÂN
-- ============================================================

CREATE TABLE favorites (
    user_id     BIGINT NOT NULL,
    movie_id    BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, movie_id),
    INDEX idx_favorites_user_time (user_id, created_at),

    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

-- Thay watch_histories: site review cần biết "muốn xem / đang xem / đã xem",
-- không cần tiến độ xem theo giây.
CREATE TABLE watchlist (
    user_id     BIGINT NOT NULL,
    movie_id    BIGINT NOT NULL,

    status      ENUM('WANT_TO_WATCH', 'WATCHING', 'WATCHED') NOT NULL DEFAULT 'WANT_TO_WATCH',
    watched_at  DATE NULL,

    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, movie_id),
    INDEX idx_watchlist_user_status (user_id, status, updated_at),

    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);


-- ============================================================
-- F. THÔNG BÁO & THỐNG KÊ
-- ============================================================

-- dedupe_key chống tạo trùng khi consumer chạy lại,
-- ví dụ 'REVIEW_LIKE:12:34' (review 12, người like 34).
CREATE TABLE notifications (
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
CREATE TABLE movie_view_daily (
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

CREATE TABLE audit_logs (
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
CREATE TABLE outbox_events (
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
