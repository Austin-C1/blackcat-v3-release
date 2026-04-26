# 黑猫 V3

黑猫 V3 是一个用于 Polymarket 监控、跟单、仓位查看和 Telegram 推送的本地工具。

## 当前功能

- 多账号管理
- 跟单交易监控
- 仓位管理
- Telegram 推送
- 大额投注监控
- 盘口投注额查询
- GitHub 在线更新

## 本地运行

```powershell
.\launch-blackcat.ps1
```

默认访问地址：

```text
http://127.0.0.1:18880
```

## 更新方式

进入公告页，点击右上角“更新”按钮。更新只覆盖程序文件，不覆盖本地配置和数据。

本地配置文件不会上传到 GitHub：

- `config/local.env.ps1`
- `config/update.json`

示例配置文件：

- `config/update.example.json`

