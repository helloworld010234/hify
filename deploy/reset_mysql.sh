#!/bin/bash
set -e

echo "=== Resetting MySQL root password ==="

# 停止 MySQL
killall mysqld 2>/dev/null || true
sleep 3

# 以安全模式启动
mysqld_safe --skip-grant-tables &
sleep 5

# 修改 root 密码
mysql -u root <<'SQL'
FLUSH PRIVILEGES;
ALTER USER 'root'@'localhost' IDENTIFIED BY 'Hify@2024';
ALTER USER 'root'@'%' IDENTIFIED BY 'Hify@2024';
FLUSH PRIVILEGES;
SQL

# 停止安全模式
killall mysqld 2>/dev/null || true
sleep 3

# 正常启动
/www/server/mysql/bin/mysqld_safe &
sleep 3

# 验证并创建数据库
mysql -u root -p'Hify@2024' -e "SELECT 'MySQL OK';"
mysql -u root -p'Hify@2024' -e "CREATE DATABASE IF NOT EXISTS hify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p'Hify@2024' -e "SHOW DATABASES LIKE 'hify';"

echo ""
echo "=== Done ==="
echo "MySQL root password: Hify@2024"
echo "Database hify created"
