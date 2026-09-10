#SingleInstance, Force
#NoEnv ; v1 指令，在 v2 中无效，但保留也无害
SetWorkingDir, %A_ScriptDir%
#Include Gdip.ahk ; 假设Gdip.ahk库存在，用于FindText功能

; 提升脚本权限
if not A_IsAdmin
{
   Run *RunAs "%A_ScriptFullPath%" %*%
   ExitApp
}

argOffset := 0
pid := ""
if (A_Args.MaxIndex() >= 8 && RegExMatch(A_Args[1], "^\d+$"))
{
    pid := A_Args[1]
    argOffset := 1
}

; 接收参数
searchText := A_Args[1 + argOffset]
err1 := A_Args[2 + argOffset]
err0 := A_Args[3 + argOffset]
X1 := A_Args[4 + argOffset]
Y1 := A_Args[5 + argOffset]
X2 := A_Args[6 + argOffset]
Y2 := A_Args[7 + argOffset]

resultFile := A_ScriptDir . "\FindTextResult.txt"
FileDelete, %resultFile% ; 每次运行前删除旧的结果文件

; 优先锁定当前 Java 进程对应的 Minecraft 窗口，避免多开串窗。
targetWin := (pid != "") ? ("ahk_pid " . pid . " ahk_class LWJGL") : "ahk_class LWJGL"

; 检查窗口是否存在
IfWinExist, %targetWin%
{
    WinActivate, %targetWin%
    WinWaitActive, %targetWin%,, 2
    
    ; 调用FindText函数
    Text_Screen_Handle := FindText(X, Y, X1, Y1, X2, Y2, err1, err0, searchText)
    
    if (Text_Screen_Handle)
    {
        FileAppend, true, %resultFile%
    }
    else
    {
        FileAppend, false, %resultFile%
    }
}
else
{
    FileAppend, false, %resultFile%
}

ExitApp
