# SignWarp

一個使用告示牌進行傳送的 Paper 插件
**(Minecraft 版本 1.21+)**

SignWarp 允許玩家放置告示牌，透過簡單的右鍵點擊在它們之間進行傳送。

預設情況下，傳送會消耗一個終界珍珠（玩家在與告示牌互動時必須持有此物品），但此設定可在配置文件中禁用。
## 權限設定

- `signwarp.create` - 允許創建傳送告示牌（預設：op）
- `signwarp.use` - 允許使用傳送告示牌（預設：所有人）
- `signwarp.reload` -  允許重新載入配置文件（預設：op）
- `signwarp.admin` - 允許進入傳送管理 GUI（預設：op）
- `signwarp.*` - 允許使用所有功能（預設：op）
- `signwarp.destroy` - 允許銷毀傳送告示牌（預設：op）

Commands:
- `/signwarp reload` - 重新載入配置文件。
- `/signwarp gui` - 開啟傳送管理 GUI。
- 可縮寫 `wp`
## 使用方式

首先，在你希望設置傳送點的位置放置一個告示牌，並填入以下內容：

- 第一行： **[WarpTarget]** 或 **[WPT]** 或 **[wpt]**
- 第二行：你想要使用的名稱

這樣會建立一個傳送告示牌，該告示牌設定了玩家傳送的目的地。

建立傳送目標告示牌後，再建立一個或多個作為傳送來源的告示牌，用以傳送到目標告示牌。
方法是放置一個告示牌並填入以下內容：

- 第一行：**[Warp]** 或 **[WP]** 或 **[wp]**
- 第二行：你想要使用的名稱

**注意：目標告示牌（WarpTarget）必須先存在，再建立傳送來源告示牌！**

建立好兩種告示牌後，你可以使用配置中設定的 `use-item` 右鍵點擊（預設為終界珍珠）來啟動傳送。
每次傳送將消耗配置中設定的物品數量（預設：1）。

你可以在 config.yml 中移除 `use-item` 選項或將其設為 "none"，這樣任何物品都可以觸發傳送（即每次傳送免費）。


## 管理 GUI

![Warps Admin](https://i.imgur.com/60JLVPC.gif)

## 訊息自訂

在 `config.yml` 配置文件中，你可以自訂插件使用的訊息。 這些訊息定義在 `messages` 這個區塊下，可以依據你的喜好進行修改。

**預覽：**

```yaml
messages:
  create_permission: "&c您沒有權限建立傳送標誌！"
  no_warp_name: "&c未設定傳送名稱！\n請在第二行設定傳送名稱。"
  warp_created: "&a傳送標誌已成功建立。"
  warp_name_taken: "&c已有相同名稱的傳送目標！"
  warp_destroyed: "&a傳送目標已被摧毀。"
  target_sign_created: "&a傳送目標標誌已成功建立。"
  destroy_permission: "&c您沒有權限摧毀傳送標誌！"
  invalid_item: "&c您必須使用 {use-item} 來啟用此傳送！"
  not_enough_item: "&c您沒有足夠的 {use-item} x{use-cost} 來建立此傳送標誌。"
  warp_not_found: "&c指定的傳送目標不存在！"
  use_permission: "&c您沒有權限使用此傳送標誌！"
  teleport: "&e正在傳送到 {warp-name}，{time} 秒後到達..."
  teleport-success: "&a成功傳送到 {warp-name}。"
  teleport-cancelled: "&c傳送已取消。"
  notify-cost: "&a您已被收取 {cost} 金幣進行傳送。"
  not_permission: "&c您沒有權限！"
  cooldown: "&c您必須等待 {cooldown} 秒才能再次傳送。"
  ```

- `{warp-name}` : 此佔位符將被替換為告示牌上指定的傳送名稱。
- `{use-item}` : 此佔位符代表使用傳送所需的物品名稱。例如，如果需要的物品是終界珍珠，則此佔位符將被替換為 "ENDER_PEARL"。
- `{use-cost}` : 此佔位符將被替換為使用傳送所需的物品數量。例如，如果傳送成本為 1 個終界珍珠，則此佔位符將被替換為 "1"。
- `{cost}` : 此佔位符將被替換為傳送所收取的貨幣數量。
- `{time}` : 此佔位符將被替換為傳送完成前的倒數秒數。
- `{cooldown}` : 此佔位符將被替換為傳送完成後的冷卻秒數。

你可以使用 Minecraft 顏色代碼來美化文字。這些代碼以 & 字符開頭，後面跟著代表特定顏色的字母或數字。 [更多資訊](https://www.digminecraft.com/lists/color_list_pc.php)

## 音效與特效自訂

你可以在 `config.yml`  配置文件中自訂傳送時播放的音效與特效：
參考[List Sound](https://www.digminecraft.com/lists/sound_list_pc.php) 和 [List Effect](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Effect.html)

**注意：音效與特效需使用大寫字母，並且音效中的 "." 必須改為 "_"。**

**預覽：**
```yaml
teleport-sound: ENTITY_ENDERMAN_TELEPORT
teleport-effect: ENDER_SIGNAL
```
## 截圖

![Plugin Screenshot](https://i.imgur.com/vrdM5sD.png)

fork from https://github.com/siriusbks/SignWarp