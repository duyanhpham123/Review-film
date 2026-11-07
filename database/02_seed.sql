-- Dữ liệu danh mục ban đầu. Chạy sau 01_schema.sql, chạy lại nhiều lần vẫn an toàn.
USE movie_web;

-- Spring Security: hasRole('ADMIN') sẽ khớp với 'ROLE_ADMIN'
INSERT IGNORE INTO roles (name) VALUES
    ('ROLE_USER'),
    ('ROLE_MODERATOR'),
    ('ROLE_ADMIN');

INSERT IGNORE INTO genres (name, slug) VALUES
    ('Hành động',    'hanh-dong'),
    ('Phiêu lưu',    'phieu-luu'),
    ('Hoạt hình',    'hoat-hinh'),
    ('Hài',          'hai'),
    ('Hình sự',      'hinh-su'),
    ('Tài liệu',     'tai-lieu'),
    ('Chính kịch',   'chinh-kich'),
    ('Gia đình',     'gia-dinh'),
    ('Giả tưởng',    'gia-tuong'),
    ('Lịch sử',      'lich-su'),
    ('Kinh dị',      'kinh-di'),
    ('Âm nhạc',      'am-nhac'),
    ('Bí ẩn',        'bi-an'),
    ('Lãng mạn',     'lang-man'),
    ('Khoa học viễn tưởng', 'khoa-hoc-vien-tuong'),
    ('Giật gân',     'giat-gan'),
    ('Chiến tranh',  'chien-tranh');

INSERT IGNORE INTO countries (code, name) VALUES
    ('VN', 'Việt Nam'),
    ('US', 'Mỹ'),
    ('GB', 'Anh'),
    ('KR', 'Hàn Quốc'),
    ('JP', 'Nhật Bản'),
    ('CN', 'Trung Quốc'),
    ('TH', 'Thái Lan'),
    ('FR', 'Pháp'),
    ('IN', 'Ấn Độ');
