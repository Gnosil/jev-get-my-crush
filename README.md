# JEV get my crush

安卓微信恋爱决策副驾。挂在微信聊天界面旁的悬浮窗：读取当前对话 → 判断你们处在哪个阶段、对方兴趣和翻车风险 → 给出 3 条候选回复（稳妥 / 进阶 / 大胆）→ 一键填入输入框。**发送永远由你自己按下。**

基于开源项目 [jev-chat-jarvis](https://github.com/jev-chat/jev-chat-jarvis)（MIT）改造，LLM 走自建中转站（OpenAI 兼容）。

## 下载

到 [Releases](../../releases) 下载最新 APK（arm64-v8a，Android 11+），安装后在设置里填中转站密钥即可用。

## 工作流

```
微信聊天界面
   │ 无障碍读取气泡（8.0.52+ 文本被隐藏时自动降级为截屏 + 本地 ML Kit OCR）
   ▼
判断模型（中转站，严格 JSON 输出，temperature 0）
   ├─ 关系阶段：刚加好友 → 破冰 → 日常互动 → 暧昧 → 邀约窗口 → 已在一起
   ├─ 翻车风险 1–9（油腻 / 越界 / 用力过猛）
   ├─ 对方是否话里有话 / 值不值得接话 / 邀约窗口是否打开
   ▼
生成模型（中转站）按阶段条件化起草 3 条候选
   ▼
判断模型排序 → 悬浮窗展示 → 点选填入输入框（SET_TEXT，失败退剪贴板）
```

## 配置

开箱默认指向我们的中转站 `https://sem.ensureok.ai/v1`（OpenAI 兼容），你只需要填一个密钥：

- **判断接口**：默认模型 `glm-5.3-flash`（快、便宜，适合做判断）。
- **回复接口**：默认模型 `glm-5.3`（负责起草候选回复）。
- 在 [中转站](https://sem.ensureok.ai) 上给自己生成一个 API Key，填进 App 设置的两个密钥框（留空时回复/视觉接口自动复用判断接口的密钥）。
- 模型名可按你的 Key 权限在设置里改。
- 密钥只存本机应用私有存储，不进代码和 git。
- 服务端地址需为 http/https 公网地址，App 拒绝内网 / 环回 / 保留地址。

判断模型不依赖 TypeSafe Jev 的 decisions API——题集被展平成严格 JSON prompt，解析失败自动重试一次，再失败该题显示为空，不影响其余分析。

## 构建

```bash
# JDK 17 + Android SDK (platforms;android-35, build-tools 35.0.0)
export JAVA_HOME=<jdk17>
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk (arm64-v8a)
```

大陆网络下 JVM 直连 dl.google.com 会被掐断，本仓库 settings.gradle.kts 已把阿里云镜像排在官方仓之前；网络不受限的机器会自动走官方仓。

## 隐私与风险声明

- 聊天内容只在分析时发往你自己配置的中转站，其余全部留在本机；「记录聊天历史」默认关闭。
- **永不自动发送**：唯一写操作是把候选文本填进输入框（或复制到剪贴板）。
- 微信对无障碍读屏和截屏有风控的可能（上游因此下架过微信支持）。本应用不做任何 hook、改包，只用无障碍读屏 + 本地 OCR，但频繁使用仍有理论风险，请自行评估。
- 建议仅供参考。真诚永远比话术好用。

## License

MIT（继承自上游 jev-chat-jarvis）。
