# MyApplication — 本地音乐播放器示例

一个基于 Kotlin 与 Jetpack Compose 的本地音乐播放器示例工程，包含播放控制、播放列表、收藏管理、以及可配置的顺序/随机播放模式。项目展示了使用前台服务驱动系统 `MediaPlayer`、Room 持久化播放数据、以及 Compose 构建现代化 UI 的常见实践。

## 主要功能
- 播放器面板：播放/暂停、上一曲/下一曲、拖动进度条、“播放列表”弹层
- 播放模式：顺序播放与随机播放（开关可持久化，默认关闭）
- 播放列表管理：添加/移除、随机排序、清空
- 收藏管理：支持多个收藏列表，歌曲加入/移除收藏
- 资源浏览：作者、专辑、收藏库浏览页面
- Now Playing 持久化：退出/重启后可恢复播放列表

## 技术栈与架构
- 语言与 UI：Kotlin、Jetpack Compose、Material3
- 播放控制：前台服务 + `MediaPlayer`（`MusicPlaybackService`）
- 控制器：`PlaybackController`（负责列表、进度、模式、与服务通信）
- 数据持久化：Room（播放列表与 Now Playing），SharedPreferences（收藏列表与播放设置）

## 关键文件
- `app/src/main/java/com/example/myapplication/MainActivity.kt`：应用入口与导航
- `app/src/main/java/com/example/myapplication/Player.kt`：播放器 UI（含播放模式开关、播放列表弹层）
- `app/src/main/java/com/example/myapplication/PlaybackController.kt`：播放控制器（维护状态并与服务交互）
- `app/src/main/java/com/example/myapplication/MusicPlaybackService.kt`：前台播放服务（驱动 `MediaPlayer`）
- `app/src/main/java/com/example/myapplication/TrackListItem.kt`：列表条目（含“正在播放”指示）
- `app/src/main/java/com/example/myapplication/FavoriteManager.kt`：收藏管理（SharedPreferences）
- `app/src/main/java/com/example/myapplication/PlaybackSettings.kt`：播放设置（顺序/随机模式开关持久化）
- `app/src/main/java/com/example/myapplication/db/*`：Room 数据库及 DAO

## 使用说明
- 打开应用后通过底部导航进入“Favorites / Authors / Albums”等页面浏览内容
- 点击右下角播放按钮打开播放器面板
- 在播放器面板：
  - 使用“播放模式”开关切换 顺序/随机 播放（该设置会持久化）
  - 在“播放列表”弹层中查看/管理列表，支持随机排序与清空
  - 进度条支持拖动定位，松手后进行 seek
  - 当前播放歌曲在播放列表中会显示播放指示图标
- 在收藏/专辑/作者页面：
  - 可将歌曲添加到播放列表或直接播放
  - 点击“播放全部”快速将页面内全部歌曲加入播放并开始播放

## 构建与运行
- 环境要求：
  - Android Studio（含 JDK）
  - Android SDK（建议使用 Android Studio 内置 SDK 管理器）
- 运行步骤：
  1. 使用 Android Studio 打开项目根目录
  2. 等待 Gradle 同步完成
  3. 选择目标设备/模拟器后点击运行
- 命令行构建（Windows）：
  - 先确保已设置 `JAVA_HOME` 指向有效 JDK（如 Android Studio 的 `jbr`）：
    - 例如：`setx JAVA_HOME "C:\\Program Files\\Android\\Android Studio\\jbr"`
  - 在项目根目录执行：`gradlew.bat assembleDebug`

## 已知说明
- 随机播放模式与“随机排序”是两种不同能力：
  - 随机播放模式影响下一曲选择策略，但不改变播放列表顺序。
  - 随机排序会打散当前播放列表的顺序。
- 在“正在播放列表”中点击已存在的歌曲时，仅跳转播放，不改变列表顺序。

## 后续可拓展
- 增加“上一曲”在随机模式下的历史回退策略
- 增加媒体通知的操作按钮与锁屏控制
- 增加本地媒体库扫描与权限处理的引导

---
本仓库主要用于演示与学习 Compose + 前台服务 + Room 的整合方式。如需进一步定制或新增页面/能力，欢迎在此基础上扩展。