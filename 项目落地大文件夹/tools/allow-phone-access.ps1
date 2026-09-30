# Run this script in PowerShell as Administrator.
# It allows phones on the same Wi-Fi to access the Changnian dev frontend and backend.

New-NetFirewallRule `
  -DisplayName "Changnian Dev 5173" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 5173 `
  -Profile Any

New-NetFirewallRule `
  -DisplayName "Changnian Backend 8080" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 8080 `
  -Profile Any

Write-Host "Done. Try opening http://192.168.16.197:5173/ on your phone."
