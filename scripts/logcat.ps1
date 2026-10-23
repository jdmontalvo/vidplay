$ErrorActionPreference = "Stop"

$adb = if ($env:ADB) { $env:ADB } else { "C:\Android\platform-tools\adb.exe" }
& $adb logcat -s VidPlay:V VidPlay.Player:V VidPlay.Vault:V VidPlay.Playback:V Media3:V ExoPlayer:V AndroidRuntime:E
