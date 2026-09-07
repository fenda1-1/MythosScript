; AHK v2 语法
; 该脚本会被 Java 高频、短时地重复拉起，必须允许并发实例，避免出现“等待旧实例关闭”的弹窗
#SingleInstance Off
SetWorkingDir(A_ScriptDir)
SetTitleMatchMode(2)

argOffset := 0
pid := ""
if (A_Args.Length >= 4 && RegExMatch(A_Args[1], "^\d+$")) {
    pid := A_Args[1]
    argOffset := 1
}

; 检查参数数量
if (A_Args.Length < 3 + argOffset) {
    ExitApp()
}

; --- 参数定义 ---
; A_Args[1]: (可选) Java 进程 PID
; A_Args[1/2]: X 坐标
; A_Args[2/3]: Y 坐标
; A_Args[3/4]: 鼠标按钮 ("Left" or "Right" or "Middle")
x := A_Args[1 + argOffset]
y := A_Args[2 + argOffset]
button := A_Args[3 + argOffset]
withShift := (A_Args.Length > 3 + argOffset and A_Args[4 + argOffset] = "true")

; 优先锁定当前 Java 进程对应的 Minecraft 窗口，避免多开串窗。
targetWin := (pid != "") ? ("ahk_pid " . pid . " ahk_class LWJGL") : "ahk_class LWJGL"

; 将 X 和 Y 坐标打包到 lParam 中，这是 Windows 消息所要求的格式
lParam := (y << 16) | x

; 根据按钮类型确定要发送的消息代码和 wParam
if (button = "Left") {
    downMsg := 0x0201 ; WM_LBUTTONDOWN
    upMsg   := 0x0202 ; WM_LBUTTONUP
    wParam  := 0x0001 ; MK_LBUTTON
} else if (button = "Middle") {
    downMsg := 0x0207 ; WM_MBUTTONDOWN
    upMsg   := 0x0208 ; WM_MBUTTONUP
    wParam  := 0x0010 ; MK_MBUTTON
} else {
    downMsg := 0x0204 ; WM_RBUTTONDOWN
    upMsg   := 0x0205 ; WM_RBUTTONUP
    wParam  := 0x0002 ; MK_RBUTTON
}

if (withShift) {
    wParam |= 0x0004 ; MK_SHIFT
}

; --- 核心逻辑：发送完整的消息序列 ---
; 1. 发送鼠标移动消息，让 GUI 元素感应到“悬浮”
PostMessage(0x0200, 0, lParam, , targetWin) ; WM_MOUSEMOVE
Sleep(20) ; 短暂延迟，确保消息被处理

; 2. 发送鼠标按下消息
PostMessage(downMsg, wParam, lParam, , targetWin)
Sleep(20)

; 3. 发送鼠标松开消息
PostMessage(upMsg, 0, lParam, , targetWin)

ExitApp()
