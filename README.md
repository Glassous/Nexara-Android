# Nexara - 智能AI聊天助手

<div align="center">
  <img src="screenshot.png" alt="Nexara Screenshot" width="300"/>
  <p><em>一款功能强大的Android AI聊天应用</em></p>
</div>

## 📱 项目简介

Nexara是一款现代化的Android AI聊天应用，为用户提供智能对话体验。应用支持多种主流AI模型，包括OpenAI GPT系列和Google AI系列，具有美观的Material Design界面和丰富的功能特性。

## ✨ 主要特性

### 🤖 多AI模型支持
- **OpenAI系列**: 支持GPT-4、GPT-3.5-turbo等模型
- **Google AI系列**: 支持Gemini等Google AI模型
- **灵活配置**: 支持自定义API端点和密钥配置
- **模型切换**: 可在不同模型间快速切换

### 💬 智能对话功能
- **流式响应**: 实时显示AI回复，提升用户体验
- **多模态支持**: 支持文本和图片输入
- **Markdown渲染**: 完美支持Markdown格式，包括表格、LaTeX公式等
- **联网搜索**: 支持AI联网搜索功能（需模型支持）

### 🎨 用户界面
- **Material Design 3**: 现代化的界面设计
- **多主题支持**: 自动/浅色/深色主题切换
- **响应式布局**: 适配不同屏幕尺寸
- **沉浸式体验**: 支持全屏显示和状态栏适配

### 📝 会话管理
- **多会话支持**: 创建和管理多个对话会话
- **会话持久化**: 自动保存对话历史
- **会话编辑**: 支持重命名和删除会话
- **智能标题**: 自动生成会话标题

### ⚙️ 高级配置
- **参数调节**: 支持Temperature、Max Tokens等参数配置
- **分组管理**: 支持模型分组管理
- **全局设置**: 统一的参数配置管理
- **配置导入导出**: 便于配置备份和迁移

## 🛠️ 技术栈

- **开发语言**: Kotlin
- **最低SDK**: Android 13 (API 33)
- **目标SDK**: Android 14 (API 36)
- **架构组件**: 
  - Material Design Components
  - RecyclerView
  - SharedPreferences
  - Coroutines
- **网络库**: OkHttp3
- **JSON处理**: Gson
- **Markdown渲染**: Markwon (支持表格、LaTeX、HTML等)

## 📦 安装说明

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11 或更高版本
- Android SDK 33+
- Gradle 8.13

### 构建步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/Glassous/Nexara-Android.git
   cd Nexara-Android
   ```

2. **打开项目**
   - 使用Android Studio打开项目
   - 等待Gradle同步完成

3. **配置API密钥**
   - 运行应用后，进入设置页面
   - 配置OpenAI或Google AI的API密钥和端点

4. **构建运行**
   ```bash
   ./gradlew assembleDebug
   ```

## 🚀 使用指南

### 首次配置

1. **启动应用**: 安装并启动Nexara
2. **进入设置**: 点击右上角设置按钮
3. **配置模型**: 
   - 选择"OpenAI系列模型配置"或"Google AI系列模型配置"
   - 添加API密钥和端点URL
   - 配置模型参数（可选）
4. **开始对话**: 返回主界面开始与AI对话

### 基本操作

- **发送消息**: 在底部输入框输入文本，点击发送按钮
- **发送图片**: 点击输入框左侧的图片按钮选择图片
- **切换模型**: 在设置中选择不同的AI模型
- **管理会话**: 通过左侧抽屉菜单管理对话会话
- **复制回复**: 点击AI回复右侧的复制按钮

### 高级功能

- **联网搜索**: 启用联网搜索开关，让AI获取实时信息
- **参数调节**: 在模型配置中调整Temperature和Max Tokens
- **主题切换**: 在设置中选择自动/浅色/深色主题
- **会话管理**: 重命名、删除或创建新的对话会话

## 📁 项目结构

```
app/src/main/
├── java/com/glassous/gsrobot/
│   ├── MainActivity.kt              # 主界面Activity
│   ├── SettingsActivity.kt          # 设置页面
│   ├── OpenAIClient.kt             # OpenAI API客户端
│   ├── GoogleAIClient.kt           # Google AI API客户端
│   ├── ChatAdapter.kt              # 聊天消息适配器
│   ├── ChatMessage.kt              # 消息数据模型
│   ├── ChatSessionManager.kt       # 会话管理器
│   ├── ThemeManager.kt             # 主题管理器
│   └── data/
│       ├── ConfigManager.kt        # 配置管理器
│       ├── GroupConfig.kt          # 分组配置模型
│       └── ModelConfig.kt          # 模型配置模型
├── res/
│   ├── layout/                     # 布局文件
│   ├── values/                     # 资源文件
│   └── drawable/                   # 图标资源
└── AndroidManifest.xml            # 应用清单
```

## 🤝 贡献指南

我们欢迎社区贡献！请遵循以下步骤：

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 📄 许可证

本项目采用MIT许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🔗 相关链接

- [OpenAI API文档](https://platform.openai.com/docs)
- [Google AI API文档](https://ai.google.dev/docs)
- [Material Design指南](https://material.io/design)

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交Issue: [GitHub Issues](https://github.com/Glassous/Nexara-Android/issues)
- 邮箱: [您的邮箱]

---

<div align="center">
  <p>Made with ❤️ by Glassous</p>
</div>