param(
    [string]$Source = (Join-Path $PSScriptRoot '../icons/icon-master.png')
)

# Resize the shared artwork; no image-generation dependencies are required.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceImage = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Source).Path)

function Save-Icon([string]$Path, [int]$Size, [int]$ArtworkSize) {
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $offset = ($Size - $ArtworkSize) / 2
        $graphics.DrawImage($sourceImage, [System.Drawing.RectangleF]::new($offset, $offset, $ArtworkSize, $ArtworkSize))
        $directory = Split-Path $Path -Parent
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

try {
    foreach ($size in @(16, 32, 48, 128)) {
        Save-Icon (Join-Path $projectRoot "icons/icon$size.png") $size $size
    }
    # Android adaptive icons use a 108dp canvas. Keep the entire emblem
    # inside the central safe area so circular launcher masks preserve it.
    Save-Icon (Join-Path $projectRoot 'android-app/app/src/main/res/drawable-nodpi/ic_launcher_art.png') 432 264
    Save-Icon (Join-Path $projectRoot 'android-app/app/src/main/res/drawable-nodpi/app_brand.png') 128 128
} finally {
    $sourceImage.Dispose()
}
