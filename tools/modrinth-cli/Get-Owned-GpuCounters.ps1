param([int]$TaskJavaProcessId,[long]$TaskJavaStartEpochMs,[string]$TaskOutputCsv)
$taskOutputPath=[IO.Path]::GetFullPath($TaskOutputCsv)
$taskOutputRoot=[IO.Path]::GetFullPath('C:\Users\Owner\Desktop\Minecraft Vulkan Hybrid\benchmarks\cumulative\diagnostics\')
if(!$taskOutputPath.StartsWith($taskOutputRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Diagnostic output outside task folder'}
if(Test-Path -LiteralPath $taskOutputPath){throw 'Preserve existing GPU telemetry'}
New-Item -ItemType Directory -Path $taskOutputRoot -Force | Out-Null
$taskWriter=[IO.StreamWriter]::new($taskOutputPath,$false,[Text.UTF8Encoding]::new($false))
$taskWriter.WriteLine('epoch_ms,engine,utilization_percent,status')
try {
 for($taskSampleIndex=0;$taskSampleIndex -lt 600;$taskSampleIndex++){
  $taskJava=Get-Process -Id $TaskJavaProcessId -ErrorAction SilentlyContinue
  if(!$taskJava){break}
  $taskCreated=[DateTimeOffset]::new($taskJava.StartTime.ToUniversalTime()).ToUnixTimeMilliseconds()
  if($taskJava.ProcessName -ne 'javaw' -or [Math]::Abs($taskCreated-$TaskJavaStartEpochMs) -gt 3000){throw 'Owned process identity changed'}
  $taskQuery='\GPU Engine(pid_'+$TaskJavaProcessId+'_*)\Utilization Percentage'
  $taskSamples=(Get-Counter -Counter $taskQuery -MaxSamples 1 -ErrorAction SilentlyContinue).CounterSamples
  $taskNow=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  foreach($taskSample in $taskSamples){
   if($taskSample.InstanceName -match '_eng_(\d+)_engtype_(.+)$'){
    $taskEngine=$matches[1]+'-'+$matches[2]
    $taskValue=$taskSample.CookedValue.ToString('R',[Globalization.CultureInfo]::InvariantCulture)
    $taskWriter.WriteLine("$taskNow,$taskEngine,$taskValue,$($taskSample.Status)")
   }
  }
  $taskWriter.Flush()
  if(!$taskSamples){Start-Sleep -Milliseconds 1000}
 }
} finally {$taskWriter.Dispose()}
