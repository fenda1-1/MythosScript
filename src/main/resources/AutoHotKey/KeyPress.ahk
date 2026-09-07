; AHK v2 语法
; 该脚本会被 Java 高频、短时地重复拉起，必须允许并发实例，避免出现“等待旧实例关闭”的弹窗
#SingleInstance Off
SetWorkingDir(A_ScriptDir)
SetTitleMatchMode(2) ; 模式2允许部分标题匹配

argOffset := 0
pid := ""
if (A_Args.Length >= 3 && RegExMatch(A_Args[1], "^\d+$")) {
    pid := A_Args[1]
    argOffset := 1
}

if (A_Args.Length < 2 + argOffset) {
    ExitApp()
}

key := A_Args[1 + argOffset]
state := A_Args[2 + argOffset]

; --- 核心修改：使用 ControlSend 实现后台发送 ---

; 优先锁定当前 Java 进程对应的 Minecraft 窗口，避免多开串窗。
targetWin := (pid != "") ? ("ahk_pid " . pid . " ahk_class LWJGL") : "ahk_class LWJGL"

; ControlSend, Control, Keys, WinTitle
; Control: 留空，表示发送到窗口本身而不是某个控件
; Keys: 要发送的按键，格式为 {Key Down} 或 {Key Up}
; WinTitle: 目标窗口
if (state = "Press") {
    ; 如果状态是 "Press"，则模拟一次完整的按下和弹起
    ControlSend("{" . key . " Down}", , targetWin)
    Sleep(30) ; 短暂延迟，模拟真实按键
    ControlSend("{" . key . " Up}", , targetWin)
} else {
    ; 否则，只发送指定的状态 (Down 或 Up)
    ControlSend("{" . key . " " . state . "}", , targetWin)
}

ExitApp()
