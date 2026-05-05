-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS `city_trip_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `city_trip_db`;

-- 2. 创建用户表
DROP TABLE IF EXISTS `trip_user`;
CREATE TABLE `trip_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(64) NOT NULL COMMENT '密码哈希',
  `password_salt` VARCHAR(32) NOT NULL COMMENT '密码盐值',
  `nickname` VARCHAR(50) NOT NULL COMMENT '显示昵称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_trip_user_username` (`username`)
) ENGINE=InnoDB COMMENT='系统用户表';

-- 3. 创建POI表
DROP TABLE IF EXISTS `poi`;
CREATE TABLE `poi` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL COMMENT 'POI名称',
  `category` VARCHAR(50) NOT NULL COMMENT '分类',
  `district` VARCHAR(50) COMMENT '所属行政区',
  `address` VARCHAR(200) COMMENT '具体地址',
  `latitude` DECIMAL(10, 6) COMMENT '纬度',
  `longitude` DECIMAL(10, 6) COMMENT '经度',
  `open_time` TIME COMMENT '开放时间',
  `close_time` TIME COMMENT '关闭时间',
  `avg_cost` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '预计人均消费',
  `stay_duration` INT NOT NULL COMMENT '建议停留时长(分钟)',
  
  -- 规则标签列 --
  `indoor` TINYINT(1) DEFAULT 0 COMMENT '1室内 0室外',
  `night_available` TINYINT(1) DEFAULT 0 COMMENT '1夜游 0否',
  `rain_friendly` TINYINT(1) DEFAULT 0 COMMENT '1雨天友好 0否',
  `walking_level` VARCHAR(20) DEFAULT '中' COMMENT '低/中/高',
  `tags` VARCHAR(255) COMMENT '偏好标签',
  `suitable_for` VARCHAR(255) COMMENT '适用群体',
  
  `description` TEXT COMMENT '简介',
  `priority_score` DECIMAL(4, 2) DEFAULT 3.0 COMMENT '系统默认权重分数',
  `crowd_penalty` DECIMAL(6, 2) DEFAULT 0.00 COMMENT '拥挤惩罚系数，值越高越拥挤'
) ENGINE=InnoDB COMMENT='POI综合表';

-- 3. 插入测试数据 (包含各种组合，满足算法约束判断)
INSERT INTO poi (name, category, district, address, latitude, longitude, avg_cost, stay_duration, indoor, night_available, rain_friendly, walking_level, tags, suitable_for, description, priority_score) 
VALUES
('武侯祠', '文化古迹', '武侯区', '武侯祠大街231号', 30.645, 104.045, 50, 120, 0, 0, 0, '中', '文化,历史,打卡', '家庭,情侣,独自', '纪念三国时期蜀汉丞相诸葛亮的祠堂', 4.5),
('锦里', '特色街区', '武侯区', '武侯祠大街231号附1号', 30.646, 104.048, 80, 90, 0, 1, 0, '低', '美食,文化,网红', '朋友,情侣,家庭,亲子', '极具蜀风雅韵的仿古商业街区', 4.8),
('杜甫草堂', '文化古迹', '青羊区', '青华路37号', 30.661, 104.025, 50, 150, 0, 0, 0, '中', '文化,历史,自然', '朋友,独自,家庭', '唐代大诗人杜甫流寓成都时的故居', 4.2),
('宽窄巷子', '特色街区', '青羊区', '长顺上街127号', 30.665, 104.053, 100, 120, 0, 1, 0, '中', '美食,文化,网红,建筑', '情侣,朋友,独自', '大清康熙年间的少城弄堂', 4.9),
('成都博物馆', '科教文化', '青羊区', '小河街1号', 30.658, 104.062, 0, 180, 1, 0, 1, '低', '文化,历史,室内', '亲子,家庭,独自,情侣', '西南地区最大的城市综合性博物馆', 4.7),
('春熙路', '商业购物', '锦江区', '春熙路', 30.657, 104.075, 200, 180, 0, 1, 0, '高', '购物,网红,美食', '情侣,朋友', '中国西部第一商业街', 4.8),
('太古里', '商业购物', '锦江区', '中纱帽街8号', 30.657, 104.081, 300, 150, 0, 1, 0, '高', '购物,网红,潮流,摄影', '情侣,朋友', '传统与现代交融的开放式街区', 4.9),
('大熊猫繁育研究基地', '自然生态', '成华区', '熊猫大道1375号', 30.730, 104.145, 55, 240, 0, 0, 0, '高', '自然,动物,网红', '亲子,家庭,情侣', '国宝大熊猫的乐园', 5.0),
('文殊院', '宗教文化', '青羊区', '文殊院街66号', 30.672, 104.066, 0, 90, 0, 0, 0, '低', '宗教,文化,静谧', '独自,家庭长辈', '川西著名佛教寺院', 4.0),
('建设路小吃街', '特色美食', '成华区', '建设巷', 30.671, 104.103, 50, 120, 0, 1, 0, '中', '美食,夜市,网红', '朋友,独自,情侣', '吃货必打卡的神仙巷子', 4.5),
('九眼桥酒吧街', '夜生活', '锦江区', '九眼桥', 30.638, 104.085, 200, 180, 0, 1, 0, '中', '夜生活,网红,音乐', '朋友,情侣', '成都夜生活最繁华的地方', 4.4),
('IFS国际金融中心', '商业购物', '锦江区', '红星路三段1号', 30.658, 104.078, 200, 120, 1, 1, 1, '中', '购物,网红,摄影', '情侣,朋友', '屋顶有著名的大熊猫雕塑', 4.6),
('青城山', '自然风光', '都江堰市', '青城山镇', 30.900, 103.568, 80, 300, 0, 0, 0, '高', '自然,运动,避暑', '朋友,家庭', '青城天下幽', 4.8),
('极地海洋公园', '休闲娱乐', '双流区', '天府大道南段2039号', 30.450, 104.070, 200, 180, 1, 0, 1, '中', '亲子,动物,室内', '亲子,家庭', '适合小朋友玩耍的海洋世界', 4.3),
('望江楼公园', '公园休闲', '武侯区', '望江路30号', 30.632, 104.088, 0, 60, 0, 0, 0, '低', '自然,休闲,静谧', '家庭长辈,独自', '为纪念唐代女诗人薛涛而建', 3.8),
('三圣花乡', '乡村旅游', '锦江区', '三圣街道', 30.585, 104.135, 100, 240, 0, 0, 0, '中', '自然,休闲,花卉', '家庭,亲子,朋友', '周末农家乐聚会好去处', 4.0),
('欢乐谷', '休闲娱乐', '金牛区', '西华大道16号', 30.718, 104.032, 230, 360, 0, 1, 0, '高', '刺激,游乐园', '朋友,情侣,亲子', '大型刺激游乐主题公园', 4.5),
('浣花溪公园', '公园休闲', '青羊区', '青华路9号', 30.659, 104.030, 0, 90, 0, 0, 0, '低', '自然,森林,散步', '家庭长辈,亲子', '成都市区最大的开放式森林公园', 4.2),
('金沙遗址博物馆', '科教文化', '青羊区', '金沙遗址路2号', 30.681, 104.011, 70, 150, 1, 0, 1, '低', '文化,历史,古蜀', '亲子,家庭,情侣', '三千年前古蜀王国的都邑', 4.6),
('东郊记忆', '文化创意', '成华区', '建设南支路4号', 30.668, 104.120, 0, 180, 0, 1, 0, '中', '艺术,摄影,潮流,工业', '朋友,情侣,独自', '老工业遗址改造的音乐公园', 4.4);
