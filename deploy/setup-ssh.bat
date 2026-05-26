@echo off
REM 在 Windows 本地执行：配置 SSH 免密登录
REM 执行完成后，即可实现自动部署

echo 正在配置 SSH 免密登录...
echo.

REM 创建 SSH 目录（如不存在）
if not exist "%USERPROFILE%\.ssh" mkdir "%USERPROFILE%\.ssh"

REM 生成密钥（如不存在）
if not exist "%USERPROFILE%\.ssh\id_rsa" (
    ssh-keygen -t rsa -b 4096 -f "%USERPROFILE%\.ssh\id_rsa" -N ""
)

REM 复制公钥到服务器（需要输入密码 Ecs123456?）
echo.
echo 请将密码: Ecs123456?
echo.
ssh-copy-id -o StrictHostKeyChecking=no root@8.136.34.168

echo.
echo 配置完成！现在可以免密登录了。
echo 测试: ssh root@8.136.34.168
echo.
pause
