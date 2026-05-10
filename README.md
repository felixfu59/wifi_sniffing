# WiFi Sniffing

一个实时监测 WiFi 信号强度和附近网络的 Android 工具应用。通过持续扫描和 RSSI 采样，帮助定位路由器位置、排查信号盲区或分析无线网络环境。

## 界面布局

```
┌──────────────────────────┐
│  [WiFi 选择列表] [速度] │  ← 顶部控制栏：选择网络 + 扫描速度
│                          │
│        ╭──────╮         │
│        │ -45  │         │  ← 中心圆形仪表盘：当前信号强度 (RSSI)
│        │ dBm  │         │
│        ╰──────╯         │
│      ▲ +3    Near       │  ← 趋势指示：方向箭头 + 变化值 + 距离估算
│     █ ▂ ▂ ▂ ▂           │  ← 信号柱状图：5 段可视化
│                          │
│  ┌─ 网络详情 ──────────┐ │
│  │ SSID       MyWiFi   │ │
│  │ BSSID      xx:xx    │ │
│  │ Frequency  5180 MHz │ │
│  │ Security   WPA2     │ │
│  │ Link Speed 866 Mbps │ │
│  │ IP Address 192...   │ │
│  └──────────────────────┘ │
└──────────────────────────┘
```

## 功能

### 核心监测
- **RSSI 实时显示** — 每秒更新当前连接的信号强度，精确到 dBm
- **信号趋势追踪** — 基于最近 6 次采样记录，计算滑动平均值判断信号走势
  - ▲ 绿色：正在靠近信号源
  - ▼ 红色：正在远离信号源
  - ● 白色：信号稳定
- **瞬时变化量** — 显示相邻两次采样的 RSSI 差值（±N dBm）
- **距离估算** — 将 RSSI 映射为五个距离区间：Very Near / Near / Medium / Far / Very Far
- **五段信号柱** — 不同颜色指示信号等级（绿 → 黄 → 橙 → 红）

### 网络扫描
- **附近网络列表** — 扫描周围所有 WiFi，按信号强度降序排列
- **未连接网络监控** — 可从列表中选择任意 WiFi（即使未连接）查看其扫描信号强度
- **失联检测** — 当被监控网络离开扫描范围时标记为"失联"并保留最后数据
- **扫描速度四档** — 极速（实时）/ 快速（2s）/ 标准（5s）/ 省电（15s）

### 连接信息
- SSID、BSSID（MAC 地址）
- 频段显示（2.4 GHz / 5 GHz / 6 GHz），含 MHz 和 GHz 双单位
- 安全类型识别：WPA3 / WPA3-SAE / WPA2 / WPA / WEP / 802.1X EAP / OWE / Open
- 链路速率（Mbps）
- IP 地址（IPv4）

### 容错设计
- `getRssi()` 返回异常值（≥0）时自动回退到扫描结果中的对应 BSSID 信号值
- 扫描卡住超过 5 秒自动重试，防止 WiFi 芯片无响应导致停止更新
- 长时间未触发扫描时强制重新启动扫描循环
- 指纹比对去重：仅在扫描结果发生变化时刷新 UI，避免无效重绘

## 项目结构

```
wifi_sniffing/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/wifi_sniffing/
│   │   │   └── MainActivity.java          # 主界面，全部核心逻辑
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml   # 界面布局
│   │   │   ├── drawable/                  # 圆形仪表盘和下拉框样式
│   │   │   └── values/                    # 颜色、字符串、主题
│   │   └── AndroidManifest.xml            # 权限声明
│   └── build.gradle.kts                   # 应用构建配置
├── build.gradle.kts                       # 项目构建配置
├── gradle/libs.versions.toml              # 版本目录
└── settings.gradle.kts
```

## 技术栈

| 类别 | 方案 |
|------|------|
| 语言 | Java 8 |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 34 |
| UI 框架 | ConstraintLayout + MaterialCardView |
| WiFi API | `WifiManager` + `ScanResult` |
| 权限 | `ACCESS_FINE_LOCATION`（运行时请求） |

## 权限

| 权限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | Android 8.0+ 扫描 WiFi 必需定位权限 |
| `ACCESS_WIFI_STATE` | 读取已连接 WiFi 信息 |
| `CHANGE_WIFI_STATE` | 主动触发 WiFi 扫描 |
| `ACCESS_NETWORK_STATE` | 获取 IP 地址等网络信息 |

## 构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

## 使用提示

- 首次启动会弹出定位权限请求，必须授权才能使用
- 选择"极速"扫描频率时，建议在 **开发者选项 → Wi-Fi 扫描节流** 中关闭限制，否则系统会强制降频
- 某些设备 Wi-Fi 芯片驱动可能返回 RSSI = 0，应用会自动用扫描结果中的值补全
- 通过观察趋势箭头和瞬时变化量，可以辅助判断与路由器的相对距离变化
