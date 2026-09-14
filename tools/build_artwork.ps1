param(
    [string]$ArtworkRoot = (Join-Path $PSScriptRoot '..\artwork')
)

Add-Type -AssemblyName System.Drawing

$sourceRoot = Join-Path $ArtworkRoot 'source'
$monitorPath = Join-Path $sourceRoot 'ae2-crafting-monitor.png'
$compassPath = Join-Path $sourceRoot 'minecraft-recovery-compass.png'

foreach ($required in @($monitorPath, $compassPath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing artwork source: $required"
    }
}

function Set-PixelRendering([System.Drawing.Graphics]$graphics) {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
}

function Draw-Mark([System.Drawing.Graphics]$graphics) {
    $monitor = [System.Drawing.Bitmap]::FromFile($monitorPath)
    $compass = [System.Drawing.Bitmap]::FromFile($compassPath)
    try {
        # Both shipped textures are 16 px. Integer scaling preserves their pixels.
        $graphics.DrawImage($monitor, [System.Drawing.Rectangle]::new(48, 48, 416, 416), 0, 0, 16, 16,
            [System.Drawing.GraphicsUnit]::Pixel)
        $graphics.DrawImage($compass, [System.Drawing.Rectangle]::new(144, 144, 224, 224), 0, 0, 16, 16,
            [System.Drawing.GraphicsUnit]::Pixel)
    }
    finally {
        $monitor.Dispose()
        $compass.Dispose()
    }
}

$transparent = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$transparentGraphics = [System.Drawing.Graphics]::FromImage($transparent)
try {
    $transparentGraphics.Clear([System.Drawing.Color]::Transparent)
    Set-PixelRendering $transparentGraphics
    Draw-Mark $transparentGraphics
    $transparent.Save((Join-Path $ArtworkRoot 'icon-transparent-512.png'),
        [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $transparentGraphics.Dispose()
}

$small = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$smallGraphics = [System.Drawing.Graphics]::FromImage($small)
try {
    $smallGraphics.Clear([System.Drawing.Color]::Transparent)
    Set-PixelRendering $smallGraphics
    $smallGraphics.DrawImage($transparent, [System.Drawing.Rectangle]::new(0, 0, 256, 256), 0, 0, 512, 512,
        [System.Drawing.GraphicsUnit]::Pixel)
    $small.Save((Join-Path $ArtworkRoot 'icon-transparent-256.png'),
        [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $smallGraphics.Dispose()
}

$curseForge = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$curseForgeGraphics = [System.Drawing.Graphics]::FromImage($curseForge)
try {
    Set-PixelRendering $curseForgeGraphics
    # Warm scheduled-craft yellow, with AE2 GUI-like dark framing.
    $curseForgeGraphics.Clear([System.Drawing.Color]::FromArgb(255, 232, 228, 199))
    $frame = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 91, 91, 82), 16)
    try {
        $curseForgeGraphics.DrawRectangle($frame, 8, 8, 496, 496)
    }
    finally {
        $frame.Dispose()
    }
    Draw-Mark $curseForgeGraphics
    $curseForge.Save((Join-Path $ArtworkRoot 'curseforge-icon-512.png'),
        [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $curseForgeGraphics.Dispose()
    $curseForge.Dispose()
    $small.Dispose()
    $transparent.Dispose()
}
