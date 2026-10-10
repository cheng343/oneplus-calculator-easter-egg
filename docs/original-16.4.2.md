# 16.4.2 彩蛋实现证据

## 输入与分析方式

用户提供旧版 APK，静态解包与反编译使用本机已有 JADX 1.5.6 / Apktool 2.12.0。没有在本机编译或重打包。

| 项目 | 旧版 | 新版 |
| --- | --- | --- |
| 包名 | com.coloros.calculator | com.coloros.calculator |
| versionName | 16.4.2 | 17.2.14 |
| versionCode | 16004002 | 17002014 |
| minSdk / targetSdk | 30 / 36 | 30 / 37 |
| 主 Fragment | h3.g | com.android.calculator2.ui.fragment.CalculatorFragment |

旧版输入 SHA-256：`99d75e7d53b9a633aad102f3f2b95d3c728351a4452f3f4dcdadbe2276c571a7`。

JADX 全量反编译报告 12 个方法错误；本次彩蛋关键方法可读，并结合 smali、布局和资源核对。完整反编译结果不上传仓库。

## 动画资源比对

三个同名文件均逐个比对 SHA-256，一致。

| 文件 | 字节 | SHA-256 | 画布 / 帧率 / 帧范围 |
| --- | ---: | --- | --- |
| never_settle_animation.json | 51838 | 921e0210f6a46c8b805c8a838be053361b9cc0d0ca603f6a7f8ac3925a4b59d9 | 822×393 / 60 / 25–121 |
| never_settle_animation_oos16_dark.json | 85772 | f3d1a2927d650cb83ab54666565fa91bbdb08d42bc3a90a67e2b8ecc65b74b72 | 1029×624 / 60 / 0–241 |
| never_settle_animation_oos16_light.json | 85775 | 345f6c9696ddd525c4901030f9d120e90f2af5b338a46800dee85747d444257b | 1029×624 / 60 / 0–241 |

模块直接读取新版 assets，不复制旧版资源。

## 方法和字段对应

| 旧版位置 | 职责 | 新版 / 模块对应 |
| --- | --- | --- |
| h3.g.Y1() | 检查整个表达式 `1+` 并调用 z2 | 在 onClick 的等号分支之前拦截 |
| h3.g.z2() | 清空表达式、布局、经典入场、系统分支 | Session.loaded / startClassic / startOos16 |
| h3.g.U1() | 清空内部求值状态和显示 | CalculatorFragment.L1() |
| h3.g.I1() | 选择 asset，MATCH_PARENT×WRAP_CONTENT，监听播放结束 | Session 构造和独立 LottieDrawable |
| h3.g.e2() / p1() | OOS16 扩散与重叠颜色动画 | Session.startOos16 / colorTransition |
| h3.g.s1() | 下一次按键时 275ms 淡出 | Session.exit |
| h3.g$f | 播放完成，恢复公式交互，保留末帧 | Session.finish |
| h3.g.onClick | 入场与播放期间拦截按键 | Session.blocksInput |
| t3.k1.H0 / h3.g.N0 | SDK >35、一加标志、confidential 条件 | c3.i1.H0 / CalculatorFragment.K0 |
| h3.g.L / d0 / j | 公式 / 根布局 / 横屏标志 | CalculatorFragment.K / d0 / j |
| n3.o | 9dp smooth round rect 裁剪 | RoundedFrame，优先 OEM OplusPath |

## 经典分支

从红线入场开始计时：

- 0–367ms：4dp 高、居中的红线，translationX 从负根布局宽度到 0。
- 367ms：加入展开背景和 Lottie，同时播放 Lottie。
- 367–1167ms：背景 scaleY 从 0 到 1。背景圆角 9dp；横屏分支为纯色矩形。
- 两段使用 PathInterpolator(0.17, 0, 0.1, 1)。
- 播放结束后，旧版监听器只恢复公式交互并设置完成状态，不额外执行黑屏动画。

## OOS16 分支

从 Lottie 播放开始计时：

- 0ms：播放当前深浅色 asset。
- 1216–1516ms：背景圆形 scaleX / scaleY 从 0 到 1，曲线 (0.3, 0, 0.1, 1)。
- 圆形直径按旧代码整数运算计算：2 × floor(sqrt(((w/2)×w)/2 + ((h/2)×h)/2))。
- 随后四个颜色 Animator 在同一个 AnimatorSet 里并行执行，保持源码的顺序和重叠，不改成串行。

| 起始色→结束色 | duration | 相对颜色组的 delay |
| --- | ---: | ---: |
| #171717（深色 #3e3e3e）→#00a1ff | 217ms | 0ms |
| #00a1ff→#1dbacc | 200ms | 0ms |
| #1dbacc→#f89832 | 416ms | 217ms |
| #f89832→#da382b | 500ms | 200ms |

颜色 Animator 保留 Android 默认插值器。通过目标应用的颜色资源读取主题值。

## 布局与退出

从当前宿主 ViewStub 对应的布局读取 padding。竖屏左右 8dp、底部 12dp，横屏及折叠设备使用它们各自的布局值。顶部区域下界为键盘/Drawer 的顶部。Lottie 是宽 MATCH_PARENT、高 WRAP_CONTENT、默认顶部位置；不再强制拉满整个公式区。

旧版是实际 ViewStub 容器，模块使用同尺寸覆盖层和独立 Lottie 6.7.1。圆角优先调用 OplusPath.addSmoothRoundRect，权重 0.99；无法访问 OEM API 时退回 Android 9dp 圆角并记录日志。

触发时清空内部表达式。播放完成后的下一次按键先启动 alpha 1→0 的 275ms 退出（曲线 0.33,0,0.67,1），再正常处理该按键。

## 验证边界

本记录来自两份 APK 静态源码/资源比对。编译、lint、签名和元数据检查交给 GitHub Actions。尚未在连接的手机上观察 0.1.2；布局、OEM 圆角和渲染差异需要真机反馈，不能据此声称像素级相同。

## 17.2.16 适配依据

样本 versionCode `17002016`。判定条件在旧版里的原始形态来自 16.4.2 的 `h3.g.Y1()` / `z2()`：

| 环节 | 旧版 16.4.2 | 17.2.14 | 17.2.16 |
| --- | --- | --- | --- |
| 清空输入 | `z2()` 第一步同步调用 `U1()` | `Reflect.call(fragment, "L1")`，按下等号时同步执行 | 同左，`L1()` 仍在 |
| 分支判定 | `t3.k1.H0()` = `Build.VERSION.SDK_INT > 35 && t3.i0.C()`，且 `!N0` | `c3.i1.H0()`，同一表达式 | **`c3.i1` 已不是那个类**：变成 COUI 工具栏工具类，只有 `a`/`b`，没有 `H0()` |
| 品牌标志 | `t3.i0.C()` → `"oneplus".equalsIgnoreCase(Build.BRAND)` | `c3.f0.C()` → 同一表达式 | **`c3.f0.F()` 被改成先调用比较再 `return false`**，即恒为 false |

因此模块不再反射 `c3.i1`，直接用平台 API 计算
`Build.VERSION.SDK_INT > 35 && "oneplus".equalsIgnoreCase(Build.BRAND) && !K0`。
这与 16.4.2、17.2.14 的判定结果一致，也不再受混淆名再次变化影响。

动画资源：17.2.16 的 `assets/` 已无 `never_settle*`，全量 smali 也没有任何文件名引用。模块把三份 JSON 打进自身 APK，读取顺序为「模块 assets → 宿主 assets」；三份文件在两版 APK 中 SHA-256 相同，见上文比对表。

字段核对：`K`（公式）、`d0`（根布局）、`j`（横屏）、`K0`（confidential）在 17.2.16 中仍然存在，`CalculatorFormula.setUserInteractionEnabled(boolean)` 与 `CalculatorFragment` 的 `L1()` / `onClick(View)` / `onPause` / `onDestroyView` / `onConfigurationChanged` 也都在。`K0` 与 `j` 读取失败时不再中断彩蛋，只记录日志并使用回退值。
