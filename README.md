# ForceScreenshots

基于 Android 无障碍服务（AccessibilityService）的截图工具：开启无障碍后，屏幕上会出现一个可拖动的悬浮球，点击即可截取当前屏幕并保存到相册。

## 功能

- **无障碍截图**：调用系统 `AccessibilityService.takeScreenshot()`（`canTakeScreenshot=true`）截图，随系统升级长期可用
- **悬浮球触发**：`TYPE_ACCESSIBILITY_OVERLAY` 悬浮球，可拖动到屏幕任意位置，点击即截图
- **保存到相册**：以 PNG 写入 `MediaStore`，无需存储权限
- **保存目录可自定义**：相册内相对路径，默认 `Pictures/Screenshots`
- **截图反馈**：截图后在屏幕上方显示「已截图」悬浮提示，自动消失

## 环境要求

- Android 11（API 30）及以上（`takeScreenshot` 的最低要求）
- 编译：Android SDK 34、JDK 17、Gradle 8.x

## 编译

```bash
# 在项目根目录创建 local.properties，写入 SDK 路径
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 签名

签名配置从根目录的 `keystore.properties` 读取，该文件已加入 `.gitignore`，不会入库：

```properties
storeFile=your.jks
storePassword=your_store_password
keyAlias=your_alias
keyPassword=your_key_password
```

若该文件不存在，则直接输出未签名包，可自行用 `apksigner` 签名。

## 使用

1. 安装并打开 App
2. 点击「打开无障碍设置」，找到「强制截图」并开启
3. 回到任意界面，点击屏幕上的红色「截」悬浮球即可截图
4. 截图保存在相册的 `Pictures/Screenshots` 目录（可在 App 内修改）

## 说明与免责

- 银行、支付等界面使用了 `FLAG_SECURE` 保护。无障碍截图走系统合成通道，**在多数机型上可截到这类界面**，但不同厂商 ROM 策略不同，部分设备可能失败。
- 本工具会获取屏幕内容，请仅用于自己的设备；请勿用于获取他人屏幕内容或规避他人的隐私保护。

## License

[MIT](LICENSE)
