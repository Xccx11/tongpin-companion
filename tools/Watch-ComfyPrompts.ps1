param(
    [string]$ComfyUrl = "http://127.0.0.1:8188",
    [string]$OutputDirectory = "D:\Users\VOS-User\Desktop",
    [int]$PollSeconds = 3
)

$ErrorActionPreference = "Stop"
$viewerStem = [string]::Concat([char[]]@(0x540C, 0x9891, 0x751F, 0x56FE, 0x63D0, 0x793A, 0x8BCD))
$htmlPath = Join-Path $OutputDirectory ($viewerStem + ".html")
$jsonlPath = Join-Path $OutputDirectory ($viewerStem + ".jsonl")
$statePath = Join-Path $OutputDirectory ".tongpin-comfy-prompt-viewer-state.json"

function Convert-ToHtml([string]$value) {
    return [System.Net.WebUtility]::HtmlEncode($value)
}

function Get-OutputFiles($entry) {
    $files = [System.Collections.Generic.List[string]]::new()
    if ($null -eq $entry.outputs) { return $files }
    foreach ($node in $entry.outputs.PSObject.Properties) {
        foreach ($collection in $node.Value.PSObject.Properties) {
            foreach ($item in @($collection.Value)) {
                if ($item.filename) { $files.Add([string]$item.filename) }
            }
        }
    }
    return $files | Select-Object -Unique
}

function Write-ViewerHtml([object[]]$records) {
    $cards = foreach ($record in ($records | Sort-Object detectedAt -Descending)) {
        $output = if ($record.outputFiles.Count) { $record.outputFiles -join ", " } else { "No output filename returned" }
        @"
<article>
  <div class="meta"><strong>$([System.Net.WebUtility]::HtmlEncode($record.detectedAt))</strong><span>Task $([System.Net.WebUtility]::HtmlEncode($record.promptId))</span></div>
  <div class="output">$([System.Net.WebUtility]::HtmlEncode($output))</div>
  <pre>$([System.Net.WebUtility]::HtmlEncode($record.actualPrompt))</pre>
</article>
"@
    }
    if (-not $cards) { $cards = '<p class="empty">Waiting for a completed ComfyUI generation...</p>' }
    $page = @"
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta http-equiv="refresh" content="5"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Tongpin Comfy Prompts</title><style>
body{margin:0;background:#f7f4f2;color:#292525;font:15px/1.55 "Microsoft YaHei",sans-serif}.wrap{max-width:1100px;margin:auto;padding:32px 22px}h1{margin:0 0 4px;font-size:25px}.sub{color:#756d6a;margin:0 0 24px}article{background:#fff;border:1px solid #e8dfdc;border-radius:14px;padding:18px;margin:14px 0}.meta{display:flex;gap:16px;flex-wrap:wrap}.meta span,.output{font-size:13px;color:#756d6a}.output{margin-top:7px;word-break:break-all}pre{white-space:pre-wrap;word-break:break-word;background:#fbf8f7;border-radius:9px;padding:14px;margin:14px 0 0;font:13px/1.55 Consolas,"Microsoft YaHei",monospace}.empty{color:#756d6a}
</style></head><body><main class="wrap"><h1>Tongpin image prompts</h1><p class="sub">Read directly from local ComfyUI history. Shows the positive prompt node 435 actually received. Refreshes every 5 seconds.</p>$cards</main></body></html>
"@
    Set-Content -LiteralPath $htmlPath -Value $page -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $OutputDirectory)) { New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null }
$seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
if (Test-Path -LiteralPath $statePath) {
    try { foreach ($id in @((Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json).seenIds)) { [void]$seen.Add([string]$id) } } catch { }
}

while ($true) {
    try {
        $history = Invoke-RestMethod -Uri "$($ComfyUrl.TrimEnd('/'))/history?max_items=100" -TimeoutSec 8
        foreach ($property in $history.PSObject.Properties) {
            $promptId = [string]$property.Name
            $entry = $property.Value
            if ($seen.Contains($promptId) -or -not $entry.status.completed) { continue }
            $node435 = $entry.prompt[2].'435'
            $actualPrompt = [string]$node435.inputs.value
            if ([string]::IsNullOrWhiteSpace($actualPrompt)) { continue }
            $record = [ordered]@{
                detectedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                promptId = $promptId
                outputFiles = @(Get-OutputFiles $entry)
                actualPrompt = $actualPrompt
            }
            ($record | ConvertTo-Json -Compress -Depth 8) | Add-Content -LiteralPath $jsonlPath -Encoding UTF8
            [void]$seen.Add($promptId)
        }
        $records = @()
        if (Test-Path -LiteralPath $jsonlPath) {
            $records = @(Get-Content -LiteralPath $jsonlPath | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
        }
        Write-ViewerHtml $records
        @{ seenIds = @($seen) } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    } catch {
        $message = Convert-ToHtml $_.Exception.Message
        Set-Content -LiteralPath $htmlPath -Encoding UTF8 -Value "<html><meta http-equiv='refresh' content='5'><body><h2>ComfyUI connection unavailable</h2><p>$message</p></body></html>"
    }
    Start-Sleep -Seconds $PollSeconds
}
