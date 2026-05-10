# WiFi Sniffing

一个实时监测 WiFi 信号强度和周围网络的 Android 应用。

## 功能

- **实时信号监测** — 每秒更新当前连接的 WiFi 信号强度 (RSSI)，支持可视化仪表盘
- **信号趋势检测** — 通过 RSSI 历史记录判断信号变化方向（靠近/远离/稳定）
- **距离估算** — 根据信号强度估算与路由器的相对距离
- **附近网络扫描** — 扫描并列表周围所有 WiFi 网络，按信号强度排序
- **任意网络监控** — 可从列表中选择任意 WiFi 网络（即使未连接）查看其信号详情
- **扫描速度调节** — 支持极速/快速/标准/省电四档扫描频率
- **连接详情** — 显示 SSID、BSSID、频段、安全类型、链路速率、IP 地址

## 权限

| 权限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | Android 扫描 WiFi 需要定位权限 |
| `ACCESS_WIFI_STATE` | 读取 WiFi 状态和信息 |
| `CHANGE_WIFI_STATE` | 触发 WiFi 扫描 |
| `ACCESS_NETWORK_STATE` | 获取网络连接信息 |

## 构建

```bash
./gradlew assembleDebug
```

## 要求

- Android 8.0+ (API 26)
- 定位权限（运行时请求）
- 如需最快扫描频率，请在开发者选项中关闭「Wi-Fi 扫描节流」
