# 情侣关系 UI 功能实现总结

## ✅ 已完成的修改

### 一、新增文件

#### 1. CoupleViewModel.kt
**路径:** `app/src/main/java/com/example/hearhome/ui/relation/CoupleViewModel.kt`

**功能:**
- 管理情侣关系的所有业务逻辑
- 处理状态管理（加载、成功、错误）
- 提供完整的情侣关系操作方法

**主要方法:**
- `getMyCouple(userId)` - 获取当前用户的情侣关系
- `getCoupleRequests(userId)` - 获取待处理的情侣请求
- `sendCoupleRequest(requesterId, partnerId)` - 发送情侣请求
- `acceptCoupleRequest(requestId, currentUserId)` - 接受请求
- `rejectCoupleRequest(requestId, currentUserId)` - 拒绝请求
- `breakupCouple(userId)` - 解除情侣关系
- `clearMessages()` - 清除消息提示

**数据模型:**
```kotlin
data class CoupleScreenState(
    val myCouple: CoupleWithPartner? = null,           // 我的情侣
    val coupleRequests: List<CoupleRequestWithRequester> = emptyList(), // 待处理请求
    val requestCount: Int = 0,                          // 请求数量
    val isLoading: Boolean = false,                     // 加载状态
    val error: String? = null,                          // 错误信息
    val successMessage: String? = null                  // 成功提示
)
```

---

### 二、修改文件

#### 1. SearchUserScreen.kt
**路径:** `app/src/main/java/com/example/hearhome/ui/search/SearchUserScreen.kt`

**修改内容:**
1. **新增导入:**
   - `CoupleViewModel`
   - `CoupleViewModelFactory`

2. **添加 CoupleViewModel:**
   ```kotlin
   val coupleViewModel: CoupleViewModel = viewModel(factory = CoupleViewModelFactory(ApiService))
   ```

3. **监听情侣请求的状态消息:**
   - 成功消息自动显示 Snackbar
   - 错误消息自动提示

4. **重新设计 UserCard 组件:**
   - 显示用户的情侣状态（"已有情侣"）
   - 添加两个操作按钮：
     - **"加好友"** - 发送好友请求（OutlinedButton）
     - **"发送情侣请求"** - 发送情侣关系请求（Button）
   - 如果对方已有情侣，禁用"发送情侣请求"按钮

**UI 效果:**
```
┌─────────────────────────────────────┐
│ [头像] 用户昵称                      │
│        ID: 123 | 性别: 男            │
│        已有情侣 (如果有的话)          │
│                                      │
│ [加好友]  [发送情侣请求]              │
└─────────────────────────────────────┘
```

---

#### 2. RelationListScreen.kt
**路径:** `app/src/main/java/com/example/hearhome/ui/relation/RelationListScreen.kt`

**修改内容:**

1. **新增导入:**
   - `CoupleViewModel`
   - `CoupleViewModelFactory`
   - `Icons.Default.HeartBroken` (解除关系图标)

2. **添加 ViewModel 和状态:**
   ```kotlin
   val coupleViewModel: CoupleViewModel = viewModel(factory = CoupleViewModelFactory(ApiService))
   val coupleState by coupleViewModel.uiState.collectAsState()
   var showBreakupDialog by remember { mutableStateOf(false) }
   ```

3. **数据刷新逻辑:**
   - 页面恢复时自动刷新情侣数据
   - 同时加载情侣关系和待处理请求

4. **TopAppBar 徽章更新:**
   - 情侣申请图标显示待处理请求数量
   - 使用 `Badge` 组件显示数字

5. **情侣信息展示:**
   - 如果有情侣关系，显示情侣卡片：
     - 伴侣头像（彩色圆圈）
     - 伴侣昵称和 ID
     - "解除关系"按钮（心碎图标）
   - 如果没有情侣，显示"暂无情侣"

6. **解除关系对话框:**
   - 确认对话框防止误操作
   - 红色警告按钮
   - 可取消操作

**UI 效果:**
```
┌─────────────────────────────────────┐
│ 我的情侣：                            │
│ ┌─────────────────────────────────┐ │
│ │ [头像] 伴侣昵称          [💔]     │ │
│ │        ID: 456                   │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ──────────────────────────────────  │
│                                      │
│ 我的好友：                            │
│ ...                                  │
└─────────────────────────────────────┘
```

---

#### 3. CoupleRequestsScreen.kt
**路径:** `app/src/main/java/com/example/hearhome/ui/relation/CoupleRequestsScreen.kt`

**修改内容:**

1. **重构为使用 API:**
   - 移除本地数据库依赖
   - 使用 `CoupleViewModel` 和 `ApiService`
   - 简化代码结构

2. **新的数据流:**
   ```
   CoupleRequestsScreen
        ↓
   CoupleViewModel.getCoupleRequests()
        ↓
   ApiService.getCoupleRequests()
        ↓
   后端 API (/couples/requests/{userId})
   ```

3. **UI 状态管理:**
   - 加载中：显示进度指示器
   - 空列表：显示"暂无新的情侣申请"
   - 有数据：显示请求列表

4. **请求卡片组件 (CoupleRequestCard):**
   - 显示请求者信息
   - 两个操作按钮：
     - **"同意"** - 接受请求
     - **"拒绝"** - 拒绝请求
   - 自动刷新列表

**UI 效果:**
```
┌─────────────────────────────────────┐
│ [头像] 请求者昵称                     │
│        ID: 789 | 性别: 女             │
│                                      │
│                          [同意]      │
│                          [拒绝]      │
└─────────────────────────────────────┘
```

---

## 🎯 功能完整流程

### 流程 1: 发送情侣请求

```
1. 用户A 搜索用户 → SearchUserScreen
   ↓
2. 找到用户B，点击"发送情侣请求"
   ↓
3. CoupleViewModel.sendCoupleRequest(A, B)
   ↓
4. ApiService.sendCoupleRequest() → 后端 API
   ↓
5. 显示成功提示："情侣请求已发送"
```

### 流程 2: 接受/拒绝请求

```
1. 用户B 进入"好友/情侣列表" → RelationListScreen
   ↓
2. 看到情侣申请徽章 (数字1)
   ↓
3. 点击情侣申请图标 → CoupleRequestsScreen
   ↓
4. 看到用户A的请求
   ↓
5. 点击"同意"
   ↓
6. CoupleViewModel.acceptCoupleRequest()
   ↓
7. 后端更新双方状态为 "in_relationship"
   ↓
8. 双方的 partnerId 互相关联
   ↓
9. 显示成功提示："已接受情侣请求"
```

### 流程 3: 查看情侣关系

```
1. 用户进入"好友/情侣列表" → RelationListScreen
   ↓
2. 页面自动加载情侣信息
   ↓
3. CoupleViewModel.getMyCouple(userId)
   ↓
4. 显示情侣卡片（头像、昵称、解除按钮）
```

### 流程 4: 解除情侣关系

```
1. 在 RelationListScreen 点击"解除关系"图标
   ↓
2. 弹出确认对话框
   ↓
3. 确认后调用 CoupleViewModel.breakupCouple(userId)
   ↓
4. 后端删除情侣关系记录
   ↓
5. 双方状态重置为 "single"
   ↓
6. 页面刷新，显示"暂无情侣"
```

---

## 📱 用户体验优化

### 1. 智能按钮状态
- **发送情侣请求按钮:**
  - 如果对方已有情侣 → 按钮禁用
  - 显示对方当前状态

### 2. 实时反馈
- 所有操作都有即时提示
- 成功消息：Snackbar/Toast
- 错误消息：自动显示并可清除

### 3. 徽章通知
- 情侣申请图标显示待处理数量
- 实时更新，无需手动刷新

### 4. 确认对话框
- 解除关系：需要确认
- 防止误操作
- 红色警告样式

### 5. 加载状态
- 网络请求时显示进度指示器
- 避免用户重复点击

---

## 🔗 与后端 API 的对接

所有功能都已连接到后端 API，使用的端点：

| 功能 | API 端点 | HTTP 方法 |
|------|----------|-----------|
| 发送请求 | `/couples/request` | POST |
| 查看我的情侣 | `/couples/{userId}` | GET |
| 查看待处理请求 | `/couples/requests/{userId}` | GET |
| 接受请求 | `/couples/accept/{requestId}` | POST |
| 拒绝请求 | `/couples/reject/{requestId}` | POST |
| 解除关系 | `/couples/{userId}` | DELETE |

---

## 🎨 UI 设计亮点

### 1. Material Design 3
- 使用 Material 3 组件
- 遵循设计规范
- 响应式布局

### 2. 颜色语义化
- 主要操作：Primary 色
- 危险操作：Error 色（红色）
- 次要操作：OutlinedButton

### 3. 图标使用
- 💕 Favorite - 情侣关系
- 👥 PersonAdd - 好友请求
- 💔 HeartBroken - 解除关系

### 4. 间距和布局
- 统一的 padding 和 spacing
- 卡片式设计
- 清晰的视觉层次

---

## ✅ 测试建议

### 手动测试流程

1. **测试发送请求:**
   - 搜索一个用户
   - 点击"发送情侣请求"
   - 验证提示信息

2. **测试接收请求:**
   - 切换到接收方账号
   - 查看情侣申请徽章
   - 进入请求列表
   - 接受或拒绝请求

3. **测试情侣展示:**
   - 进入好友/情侣列表
   - 验证情侣信息显示正确

4. **测试解除关系:**
   - 点击解除按钮
   - 确认对话框
   - 验证关系已解除

5. **边界测试:**
   - 向已有情侣的用户发送请求（按钮应禁用）
   - 向自己发送请求（应该有防护）
   - 网络错误情况

---

## 📝 代码质量

### 优点
✅ 使用 MVVM 架构  
✅ 状态管理清晰（StateFlow）  
✅ 错误处理完善  
✅ UI 和业务逻辑分离  
✅ 代码复用性好  
✅ 符合 Kotlin 编码规范  
✅ 无 Linter 错误  

### 注意事项
⚠️ 需要网络连接才能使用  
⚠️ 部分操作需要等待后端响应  
⚠️ 建议添加离线缓存机制  

---

## 🚀 下一步建议

### 短期（可选）
1. 添加加载动画优化体验
2. 实现下拉刷新功能
3. 添加情侣空间快捷入口

### 中期（可选）
1. 情侣关系历史记录
2. 情侣互动统计
3. 情侣专属标识

### 长期（可选）
1. 情侣成就系统
2. 纪念日提醒集成
3. 情侣主题定制

---

## 📞 问题反馈

如遇到问题：
1. 检查网络连接
2. 验证后端服务是否运行
3. 查看 Logcat 日志
4. 确认用户权限

---

**实现完成时间:** 2025-11-27  
**状态:** ✅ 已完成，可以测试使用  
**版本:** v1.0  

所有功能已实现并通过编译！🎉

