# 🚀 SignWarp

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4+-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)

**一個功能豐富且易於使用的 Minecraft Paper 插件，讓玩家透過告示牌輕鬆建立傳送系統**

[功能特色](#-功能特色) • [安裝說明](#-安裝說明) • [使用方式](#-使用方式) • [配置設定](#-配置設定) • [權限系統](#-權限系統)

</div>

---

## 📖 簡介

SignWarp 是一款專為 Minecraft Paper 伺服器設計的傳送插件，讓玩家能夠透過簡單的告示牌系統建立個人或公共的傳送點。無論是建立城市間的交通網絡，還是設置私人基地的快速通道，SignWarp 都能滿足您的需求。

## ✨ 功能特色

### 🎯 核心功能
- **📝 簡易告示牌系統** - 透過放置告示牌輕鬆建立傳送點
- **🔐 權限管理** - 支援公共與私人傳送點設定
- **👥 邀請系統** - 允許分享私人傳送點給特定玩家
- **🎮 管理介面** - 提供 GUI 管理所有傳送點
- **⚡ 即時傳送** - 支援多種傳送狀態與載具

### 🚗 進階傳送支援
- **🐎 騎乘傳送** - 騎馬、豬等載具時一同傳送
- **⛵ 船隻傳送** - 包含船上的實體（動物、怪物）一起傳送
- **🪢 韁繩傳送** - 手持韁繩時連同綁定實體一起傳送
- **🛡️ 安全機制** - 船上有其他玩家時不會進行傳送

### 🎨 自訂化選項
- **💬 訊息自訂** - 完全可自訂的插件訊息
- **🔊 音效特效** - 可配置傳送音效與視覺效果
- **⏱️ 冷卻系統** - 可設定傳送延遲與冷卻時間
- **💎 物品消耗** - 可設定傳送所需物品（預設：終界珍珠）
- **🏗️ 傳送點數量自訂** - 可設定每人最多建立傳送點(WarpTarget)數量

## 🛠️ 安裝說明

### 系統需求
- **Minecraft 版本**: 1.21.4 或更高版本
- **伺服器軟體**: Paper（推薦）或其他基於 Paper 的核心
- **Java 版本**: Java 21 或更高版本

### 安裝步驟
1. 下載最新版本的 SignWarp.jar 檔案
2. 將檔案放入伺服器的 `plugins` 資料夾
3. 重新啟動伺服器
4. 編輯 `config.yml` 來自訂設定（可選）

## 🎮 使用方式

### 建立傳送目標 (WarpTarget)

1. **放置告示牌** 在您想要設置為傳送目的地的位置
2. **編輯告示牌內容**：
   ```
   第一行: [WarpTarget] 或 [WPT] 或 [wpt]
   第二行: 傳送點名稱（例如：家）
   ```

### 建立傳送來源 (Warp)

1. **放置告示牌** 在您想要作為傳送起點的位置
2. **編輯告示牌內容**：
   ```
   第一行: [Warp] 或 [WP] 或 [wp]
   第二行: 目標傳送點名稱（必須已存在））
   ```

### 使用傳送

1. **手持所需物品**（預設：終界珍珠）
2. **右鍵點擊** 傳送來源告示牌
3. **等待倒數** 完成後即可傳送

> ⚠️ **重要提醒**: 必須先建立傳送目標 (WarpTarget) 才能建立對應的傳送來源 (Warp)

## 🔧 配置設定

### 基本設定

```yaml
# 使用傳送所需物品（設為 none 表示免費）
use-item: ENDER_PEARL

# 每次傳送消耗數量
use-cost: 1

# 傳送前延遲時間（秒）
teleport-delay: 5

# 傳送後冷卻時間（秒）
teleport-use-cooldown: 10

# 預設傳送點可見性（false=公共, true=私人）
default-visibility: false
```

### 傳送點(WarpTarget)設定

```yaml
# 建立傳送目標所需物品
create-wpt-item: "DIAMOND_SWORD"

# 建立傳送目標消耗數量
create-wpt-item-cost: 1

# 每位玩家最多可創建的傳送點數量（-1 表示無限制）
max-warps-per-player: 10

# OP 是否不受創建數量限制（true 表示 OP 無限制）
op-unlimited-warps: true
```

### 音效與特效

```yaml
# 傳送音效
teleport-sound: minecraft:entity.enderman.teleport

# 傳送特效
teleport-effect: ENDER_SIGNAL
```

## 🎯 指令系統

| 指令 | 縮寫 | 功能描述 | 權限需求 |
|------|------|----------|----------|
| `/signwarp reload` | `/wp reload` | 重新載入配置檔案 | `signwarp.reload` |
| `/signwarp gui` | `/wp gui` | 開啟管理介面 | `signwarp.admin` |
| `/signwarp set <公共\|私人> <傳送點>` | `/wp set` | 設定傳送點可見性 | `signwarp.private.set` |
| `/signwarp invite <玩家> <傳送點>` | `/wp invite` | 邀請玩家使用私人傳送點 | `signwarp.invite` |
| `/signwarp uninvite <玩家> <傳送點>` | `/wp uninvite` | 移除玩家邀請 | `signwarp.invite` |
| `/signwarp list-invites <傳送點>` | `/wp list-invites` | 查看邀請列表 | `signwarp.invite.list` |
| `/signwarp tp <傳送點>` | `/wp tp` | 直接傳送（管理員） | `signwarp.tp` |

## 🔐 權限系統

### 基礎權限

| 權限節點 | 預設 | 描述 |
|----------|------|------|
| `signwarp.*` | OP | 所有權限 |
| `signwarp.create` | OP | 建立傳送告示牌 |
| `signwarp.use` | 所有人 | 使用傳送功能 |
| `signwarp.destroy` | OP | 破壞傳送告示牌 |
| `signwarp.admin` | OP | 使用管理功能 |
| `signwarp.reload` | OP | 重新載入配置 |

### 進階權限

| 權限節點 | 預設 | 描述 |
|----------|------|------|
| `signwarp.private.use` | OP | 使用他人私人傳送點 |
| `signwarp.private.set` | 所有人 | 設定傳送點可見性 |
| `signwarp.invite` | 所有人 | 邀請/移除邀請功能 |
| `signwarp.invite.list` | 所有人 | 查看邀請列表 |
| `signwarp.invite.admin` | OP | 管理任何傳送點邀請 |
| `signwarp.tp` | OP | 直接傳送指令 |

## 📱 管理介面

SignWarp 提供直觀的 GUI 管理介面，讓管理員能夠：

- 📋 查看所有傳送點列表
- 🔍 檢視傳送點詳細資訊
- 👁️ 查看傳送點可見性狀態

使用 `/wp gui` 指令開啟管理介面。

![管理介面預覽](https://i.imgur.com/60JLVPC.gif)

## 🎨 訊息自訂

所有插件訊息都可以在 `config.yml` 中自訂，支援 Minecraft 顏色代碼：

```yaml
messages:
  teleport-success: "&a成功傳送到 {warp-name}！"
  private_warp: "&c這是私人傳送點，需要邀請才能使用。"
  invite_success: "&a已邀請 {player} 使用傳送點 '{warp-name}'！"
```

### 可用佔位符

- `{warp-name}` - 傳送點名稱
- `{player}` - 玩家名稱
- `{inviter}` - 邀請者名稱
- `{use-item}` - 所需物品名稱
- `{use-cost}` - 物品消耗數量
- `{time}` - 倒數時間
- `{cooldown}` - 冷卻時間
- `{visibility}` - 可見性狀態
- `{current}` - 玩家目前傳送點(WarpTarget)數量
- `{max}` - 玩家最大可建立傳送點(WarpTarget)數量

## 📸 遊戲截圖

![插件使用示例](https://i.imgur.com/vrdM5sD.png)

## 🤝 支援與回饋

如果您在使用過程中遇到任何問題或有功能建議，歡迎：

- 🐛 [回報問題](https://github.com/verdo568/SignWarp/issues)
- 💡 [提出功能請求](https://github.com/verdo568/SignWarp/issues)
- ⭐ 給我們一個星星來支持開發

## 📄 授權條款

本專案使用 [MIT 授權條款](LICENSE)。

## 🙏 致謝

本專案 fork 自 [siriusbks/SignWarp](https://github.com/siriusbks/SignWarp)，感謝原作者的優秀工作。