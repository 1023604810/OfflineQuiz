# 离线刷题助手（OfflineQuiz）

纯离线、单机运行的 Android 刷题 App。题目通过本地文件导入，做题记录 / 错题 / 收藏全部保存在本机
Room 数据库，**不申请网络权限**，断网环境下可完整使用。

> **题库说明**：本仓库**不包含任何题库数据、解析、题库文档或 APK**。使用前请自行准备符合格式的
> 题库文件，通过 App 内的「导入题库」加载；本地开发如需内置题库，可把 JSON 放到
> `app/src/main/assets/questionbank/ict.json`，该目录已被 `.gitignore` 忽略，不会进入版本库。
> 未放置题库时，界面上的「导入内置题库」入口会自动隐藏。

## 功能

- **题库导入**：SAF 选择 JSON / CSV / .xlsx，校验必填字段、自动识别编码、MD5 去重；
  按题干精确匹配：已存在则**原地覆盖**，不存在则插入，结果区分「新增」与「覆盖更新」；
  多选答案支持 `ABD`、`ACD` 这类非连续字母组合。
- **练习**：开始练习（全部题目，支持断点续练）、每日一练（5/10/20/50 题，日期种子）、
  按难度练习、收藏练习、错题重做。
- **背题 / 刷题**：答题页右上角实时切换，保留题号与已选内容；背题模式直接展示答案与解析，
  不写做题记录。
- **答题**：单选、多选（半对）、判断、填空（多空）、主观题（自评）；提交后展示对错、标准答案、
  解析；「上一题」按浏览历史回退并恢复已填答案与判分结果。
- **错题本**：答错自动收录、错误次数累计、重做答对可移出、左滑移除、重做全部。
- **收藏**：答题页星标、收藏列表、取消收藏、收藏练习。
- **模考**：按题型/难度/知识点分层抽题、题量不足兜底、倒计时、答题卡、自动交卷、成绩与题型分布。
- **统计**：近 7 天做题量与正确率、知识点掌握度与薄弱项。
- **自动难度分级**：知识点覆盖度 + 题干结构复杂度 + 实测易错率计算 0–100 分，
  映射简单/中等/困难，导入后与启动时自动刷新。
- **无配图题清理**：可选的数据清理逻辑，按本地删除清单精确移除「引用配图但源文件无图」的题目。

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Java 8、Gradle 8.9、AGP 8.5.2 |
| 数据库 | Room 2.6.1（SQLite，v3，含 v1→v2→v3 迁移） |
| JSON | Gson 2.10.1 |
| Excel | 自研流式 .xlsx 解析（ZipInputStream + XmlPullParser），不依赖 Apache POI |
| 架构 | Activity/Fragment → Repository/Service → Room；LiveData 驱动 UI，阻塞操作走线程池 |
| 文件选择 | Storage Access Framework |
| UI | Material Components、ViewBinding、自定义 View |

## 构建

环境要求：JDK 17+（实测 JDK 21）、Android SDK（platform 34、build-tools 34.0.0）。

```bash
# 1) 配置 SDK 路径（local.properties，已被 git 忽略）
#    sdk.dir=D\:\\Program Files\\Android\\SDK

# 2) 编译
gradle assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease     # 未签名包，签名请使用自己的 keystore

# 3) 单元测试（不依赖任何内置题库数据）
gradle testDebugUnitTest
```

签名（示例，仓库不包含任何密钥）：

```bash
apksigner sign --ks my-release.jks --out app-release.apk app-release-unsigned.apk
```

## 题库格式

### 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `stem` | ✅ | 题干，支持换行 |
| `type` | ✅ | `single` / `multiple` / `judge` / `blank` / `essay`（缺失时按选项与答案推断） |
| `options` | 选择题必填 | `["A. 选项一", "B. 选项二"]`，缺少字母前缀会自动补 |
| `answer` | ✅ | 单选 `"A"`；多选 `"ABD"`；判断 `"T"/"F"`；填空 `"第一空|第二空"`；主观题为参考答案 |
| `analysis` | 否 | 解析；缺失时导入后自动生成并写回 |
| `knowledge` | 否 | 知识点标签，如 `网络基础/路由` |
| `difficulty` | 否 | 1 简单 / 2 中等 / 3 困难（导入后按算法重新分级） |
| `id` | 否 | 外部编号 |

### JSON 示例

```json
{
  "subject": "示例学科",
  "version": "1.0",
  "questions": [
    {
      "id": "Q001",
      "stem": "以下哪个是 Java 的基本数据类型？",
      "type": "single",
      "options": ["A. int", "B. String", "C. Integer", "D. Object"],
      "answer": "A",
      "analysis": "int 是基本数据类型，String/Integer/Object 都是引用类型。",
      "knowledge": "Java 基础/数据类型",
      "difficulty": 1
    }
  ]
}
```

### CSV / XLSX

首行为表头，支持中英文列名（`stem/题干`、`type/题型`、`options/选项`、`answer/答案`、
`analysis/解析`、`knowledge/知识点`、`difficulty/难度`、`id/编号`）。
选项单元格内用换行分隔，也兼容 `|`；CSV 自动识别 UTF-8 / GBK / GB18030。

## 目录结构

```
OfflineQuiz/
├─ app/src/main/java/com/example/offlinequiz/
│  ├─ App.java                     # 数据库、线程池、启动维护任务
│  ├─ data/                        # Room 实体 / DAO / 模型 / Repository
│  ├─ engine/                      # 判分、难度分析、解析生成
│  ├─ importer/                    # JSON / CSV / XLSX 解析与导入
│  ├─ service/                     # 每日一练、组卷、难度校准、新增/覆盖、清理
│  ├─ ui/                          # 首页、答题、模考、错题、收藏、统计、导入、设置
│  └─ util/                        # 日期、哈希、文本、偏好、题库可用性检查
├─ app/src/main/res/               # 布局、样式、图标
├─ app/src/test/java/...           # 单元 / Robolectric 测试（不依赖内置题库）
├─ build.gradle / settings.gradle / gradle.properties
└─ README.md
```

> 题库资源目录、题库解析工具与含题目内容的文档不在仓库中；`.gitignore` 已覆盖这些路径。

## 数据与隐私

- 所有题库、做题记录、错题、收藏、模考数据只保存在本机 SQLite；
- 未声明 `INTERNET` 权限，App 不发起任何网络请求；
- 卸载 App 会一并删除本地数据。

## 版本

当前版本 1.4.1（versionCode 6）。
