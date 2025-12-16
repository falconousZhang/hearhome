# WorkManager 依赖添加指南

## 问题描述
编译时出现 `Unresolved reference 'work'` 等错误，这是因为项目缺少 WorkManager 依赖。

## 已完成的修改
已在 `app/build.gradle.kts` 文件中添加了 WorkManager 依赖：
```kotlin
// WorkManager for background tasks
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

## 下一步操作

### 方法 1：在 Android Studio 中同步（推荐）
1. 在 Android Studio 中打开项目
2. 点击顶部工具栏的 "Sync Project with Gradle Files" 按钮（或按 Ctrl+Shift+O）
3. 等待同步完成
4. 重新构建项目

### 方法 2：使用命令行（需要 Java 17）
如果你想使用命令行构建，需要确保使用 Java 17：

```powershell
# 检查 Java 版本
java -version

# 如果不是 Java 17，需要安装 Java 17 并设置环境变量
# 下载地址：https://adoptium.net/
```

然后执行：
```powershell
cd d:\lvhome\lovehome
.\gradlew clean build
```

## 验证
同步完成后，以下类应该能够正确解析：
- `androidx.work.Worker`
- `androidx.work.CoroutineWorker`
- `androidx.work.WorkManager`
- `androidx.work.WorkerParameters`
- `androidx.work.Constraints`
- `androidx.work.NetworkType`
- `androidx.work.PeriodicWorkRequestBuilder`
- `androidx.work.BackoffPolicy`
- `androidx.work.ExistingPeriodicWorkPolicy`

## 关于数据库脚本
之前的问题是关于执行 SQL 脚本。这两个问题是独立的：

1. **前端问题（当前）**：需要添加 WorkManager 依赖
2. **后端问题**：需要在服务器上执行 SQL 脚本创建数据库表

### 执行数据库脚本的正确方法
如果数据库在远程服务器（`121.37.136.244`），你需要：

```bash
# 1. 连接到服务器
ssh user@121.37.136.244

# 2. 上传 SQL 文件到服务器
# 使用 scp 或其他方式上传 create_space_pets_table.sql

# 3. 在服务器上执行脚本
mysql -uroot -p lovehome < /path/to/create_space_pets_table.sql
```

或者使用 MySQL 客户端工具（如 MySQL Workbench、DBeaver 等）连接到远程数据库后执行脚本。

## 总结
- **前端更新**：已添加 WorkManager 依赖，需要在 Android Studio 中同步项目
- **后端更新**：需要在数据库服务器上执行 SQL 脚本（如果还没执行的话）
- 两个更新都需要完成，前端才能正常使用宠物功能
