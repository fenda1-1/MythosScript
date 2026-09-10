; AHK v2 语法
; 该脚本会被 Java 高频、短时地重复拉起，必须允许并发实例，避免出现“等待旧实例关闭”的弹窗
#SingleInstance Off
SetWorkingDir(A_ScriptDir)
SetTitleMatchMode(2)

argOffset := 0
pid := ""
if (A_Args.Length >= 5 && RegExMatch(A_Args[1], "^\d+$")) {
    pid := A_Args[1]
    argOffset := 1
}

; 检查参数数量是否足够
if (A_Args.Length < 4 + argOffset) {
    ExitApp()
}

; --- 参数定义 ---
; A_Args[1]: (可选) Java 进程 PID
; A_Args[1/2]: 窗口标题 (为兼容性保留，但在此脚本中不再直接使用)
; A_Args[2/3]: X 坐标
; A_Args[3/4]: Y 坐标
; A_Args[4/5]: 鼠标按钮 ("Left" or "Right")
; A_Args[5/6]: (可选) 是否移动鼠标 ("true" 或 其他)
x := A_Args[2 + argOffset]
y := A_Args[3 + argOffset]
button := A_Args[4 + argOffset]
; 检查第5个参数是否存在且是否为 "true"
moveMouse := (A_Args.Length > 4 + argOffset and A_Args[5 + argOffset] = "true")
withShift := (A_Args.Length > 5 + argOffset and A_Args[6 + argOffset] = "true")

; 优先锁定当前 Java 进程对应的 Minecraft 窗口，避免多开串窗。
targetWin := (pid != "") ? ("ahk_pid " . pid . " ahk_class LWJGL") : "ahk_class LWJGL"

; --- 核心修复：根据 moveMouse 参数决定点击方式 ---

if (moveMouse) {
    ; --- 移动鼠标模式 (兼容模式) ---
    ; 设置坐标模式为相对于窗口客户区
    CoordMode "Mouse", "Client"
    ; 激活游戏窗口，确保它是最前面的窗口
    WinActivate(targetWin)
    ; 等待窗口激活，增加稳定性，最多等待1秒
    WinWaitActive(targetWin,, 1)
    ; 移动鼠标到计算出的坐标
    MouseMove(x, y)
    ; 短暂延迟，确保鼠标移动到位
    Sleep(50)
    if (withShift) {
        Send("{Shift down}")
    }
    Click(button)
    if (withShift) {
        Send("{Shift up}")
    }
} else {
    ; --- 静默点击模式 (后台模式) ---
    ; ControlClick可以直接在后台向窗口的指定坐标发送点击消息
    ; 它不需要激活窗口，也不会移动用户的实际鼠标指针。
    ; 参数:
    ;   - Control-or-Pos: 使用 "x" . x . " y" . y 指定坐标
    ;   - WinTitle: 我们的目标窗口
    ;   - WinText: 留空
    ;   - Button: 点击的按钮
    ;   - ClickCount: 点击次数
    ;   - Options: NA 选项表示不激活窗口并且使用更可靠的发送模式
    if (withShift) {
        Send("{Shift down}")
    }
    ControlClick("x" . x . " y" . y, targetWin, , button, 1, "NA")
    if (withShift) {
        Send("{Shift up}")
    }
}

ExitApp()
