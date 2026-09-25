@echo off
setlocal
cd /d "%~dp0"
set LOG=train_log.txt
set PY=.venv\Scripts\python.exe
echo ==== %DATE% %TIME% ==== > %LOG%

if not exist ".venv\Scripts\pip.exe" (
  echo [setup] bootstrapping pip in .venv >> %LOG%
  %PY% -m ensurepip --upgrade >> %LOG% 2>&1
)

where nvidia-smi >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [setup] NVIDIA GPU found - ensuring CUDA build of torch >> %LOG%
  %PY% -c "import torch,torchvision;torchvision.ops.nms(torch.zeros(1,4,device='cuda'),torch.zeros(1,device='cuda'),0.5)" >nul 2>&1
  if errorlevel 1 (
    %PY% -m pip install --retries 10 --timeout 60 "torch==2.5.1+cu121" "torchvision==0.20.1+cu121" --index-url https://download.pytorch.org/whl/cu121 >> %LOG% 2>&1
    %PY% -m pip install --retries 10 --timeout 60 --force-reinstall --no-deps "torchvision==0.20.1+cu121" --index-url https://download.pytorch.org/whl/cu121 >> %LOG% 2>&1
  )
)
%PY% -m pip install --retries 10 --timeout 60 -r requirements.txt >> %LOG% 2>&1
%PY% -c "import torch,ultralytics;import torchvision;print('torch',torch.__version__,'torchvision',torchvision.__version__,'cuda',torch.cuda.is_available(),'ultralytics',ultralytics.__version__)" >> %LOG% 2>&1

where nvidia-smi >nul 2>&1
if %ERRORLEVEL%==0 (
  %PY% -c "import torch,torchvision;torchvision.ops.nms(torch.zeros(1,4,device='cuda'),torch.zeros(1,device='cuda'),0.5)" >nul 2>&1
  if errorlevel 1 (
    echo [error] GPU present but CUDA torch not installed - re-run this script >> %LOG%
    goto :eof
  )
)

rem --batch -1 = AutoBatch: picks the largest batch that fits in GPU memory
rem (batch 16 at imgsz 1280 does not fit on a 4 GB RTX 3050).
echo [train] starting >> %LOG%
%PY% train.py --model yolov8s.pt --epochs 150 --imgsz 1280 --data mlbb_data.yaml --batch -1 %* >> %LOG% 2>&1
echo [done] exit code %ERRORLEVEL% >> %LOG%
