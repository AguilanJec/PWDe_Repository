@echo off
setlocal enabledelayedexpansion

rem Requires ffmpeg and ffprobe on PATH (https://ffmpeg.org/download.html).
rem Mirrors the bash version: extracts 1 frame every 2 seconds per video,
rem into extracted_frames\<name>_<width>x<height>\frame_0001.jpg etc.

if not exist "raw_footage" (
    echo No "raw_footage" folder found next to this script.
    exit /b 1
)

if not exist "extracted_frames" mkdir "extracted_frames"

for %%f in (raw_footage\*.mp4) do (
    set "name=%%~nf"
    set "res="

    for /f "delims=" %%r in ('ffprobe -v error -select_streams v:0 -show_entries "stream=width,height" -of "csv=s=x:p=0" "%%f"') do (
        set "res=%%r"
    )

    if "!res!"=="" (
        echo WARNING: could not read resolution for %%f, skipping.
    ) else (
        set "outdir=extracted_frames\!name!_!res!"
        if not exist "!outdir!" mkdir "!outdir!"
        echo Extracting %%f -^> !outdir!
        ffmpeg -i "%%f" -vf "fps=1/2" -q:v 2 "!outdir!\frame_%%04d.jpg"
    )
)

echo Done.
endlocal