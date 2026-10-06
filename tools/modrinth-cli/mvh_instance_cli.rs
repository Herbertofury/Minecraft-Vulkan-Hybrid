use std::path::PathBuf;
use std::time::Duration;
use theseus::install::InstallJobStatus;
fn main() {
 let result=std::thread::Builder::new().name("mvh-cli".into()).stack_size(32*1024*1024).spawn(||{
  let rt=tokio::runtime::Builder::new_multi_thread().enable_all().worker_threads(4).thread_stack_size(16*1024*1024).build().unwrap();
  rt.block_on(async_main()).map_err(|e|e.to_string())
 }).unwrap().join().unwrap();
 if let Err(e)=result {eprintln!("MVH_ERROR {}",e);std::process::exit(1);}
}
async fn async_main() -> Result<(), Box<dyn std::error::Error>> {
    let arg=std::env::args().nth(1).ok_or("Expected benchmark .mrpack or launch")?;
    if arg=="launch" || arg=="metadata" || arg=="configure" {
      let id=std::env::args().nth(2).ok_or("Expected isolated instance id")?;
      theseus::EventState::init().await?;
      theseus::State::init_mvh_cli("ModrinthApp".to_string()).await?;
      if arg=="metadata" {let m=theseus::metadata::mvh_refresh_forge_versions().await?;println!("{}",serde_json::to_string(&m)?);return Ok(());}
      if !["local:cdb476fb-b03b-4145-a054-17e041603944","local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3"].contains(&id.as_str()){return Err("Only the two task-created benchmark instance IDs are accepted".into());}
      let runtime_data=std::env::current_exe()?.parent().ok_or("Missing executable directory")?.join("runtime/forge-47.4.26");
      let instance=theseus::instance::get(&id).await?.ok_or("Unknown instance")?;
      if !instance.instance.name.starts_with("MVH isolated "){return Err("Refusing to operate on a personal instance".into());}
      let base:daedalus::minecraft::VersionInfo=serde_json::from_slice(&std::fs::read(runtime_data.join("minecraft-1.20.1.json"))?)?;
      let partial:daedalus::modded::PartialVersionInfo=serde_json::from_slice(&std::fs::read(runtime_data.join("version.json"))?)?;
      let mut merged=daedalus::modded::merge_partial_version(partial,base);
      merged.id="1.20.1-47.4.26".into();
      let runtime_dir=PathBuf::from("C:/Users/Owner/AppData/Roaming/ModrinthApp/meta/versions/1.20.1-47.4.26");std::fs::create_dir_all(&runtime_dir)?;
      let target=runtime_dir.join("1.20.1-47.4.26.json");
      let bytes=serde_json::to_vec(&merged)?;
      if target.exists(){if serde_json::from_slice::<serde_json::Value>(&std::fs::read(&target)?)?!=serde_json::from_slice::<serde_json::Value>(&bytes)?{return Err("Refusing to overwrite differing runtime metadata".into());}}else{std::fs::write(&target,bytes)?;}
      let patch:theseus::prelude::EditInstance=serde_json::from_value(serde_json::json!({"content_set_patch":{"loader_version":"47.4.26"},"launch_overrides":{"java_path":"C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/javaw.exe","extra_launch_args":["-Dorg.lwjgl.system.stackSize=1024","-Dharimt.fps.auto=true","-Dharimt.fps.warmupSeconds=60"],"memory":{"maximum":8192},"force_fullscreen":false,"game_resolution":[1920,1080]}}))?;
      theseus::instance::edit(&id,patch).await?;
      for opt in theseus::data::InstanceSyncedOption::ALL {if opt.is_available(){theseus::instance::set_synced_option(&id,opt,false,None).await?;}}
      if arg=="configure" {println!("CONFIGURED {}",id);return Ok(());}
      let proc=theseus::instance::run(&id,theseus::instance::QuickPlayType::Singleplayer("MVH-Benchmark-20261004".to_string())).await?;
      println!("PROCESS {}",proc.uuid);
      theseus::process::wait_for(proc.uuid).await?;
      println!("PROCESS_FINISHED");
      return Ok(());
    }
    let pack=PathBuf::from(arg);
    let expected=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/modrinth");
    if pack.parent()!=Some(expected.as_path()) || !pack.file_name().unwrap().to_string_lossy().starts_with("baseline-2.4.9") && !pack.file_name().unwrap().to_string_lossy().starts_with("candidate-2.4.10") {return Err("Only the two prepared isolated benchmark packs are accepted".into());}
    theseus::EventState::init().await?;
    theseus::State::init_mvh_cli("ModrinthApp".to_string()).await?;
    let job=theseus::install::create_modpack_instance(theseus::pack::install_from::CreatePackLocation::FromFile{path:pack},None).await?;
    let id=job.job_id.parse()?;
    println!("JOB {}",job.job_id);
    loop {
      let j=theseus::install::get_job(id).await?;
      match j.status {
       InstallJobStatus::Succeeded=>{println!("INSTANCE {}",j.instance_id.unwrap_or_default());break;},
       InstallJobStatus::Failed|InstallJobStatus::Interrupted|InstallJobStatus::Canceled=>{println!("FAILED {}",serde_json::to_string(&j.error)?);return Err("Isolated pack installation did not succeed".into());},
       _=>{}
      }
      tokio::time::sleep(Duration::from_secs(1)).await;
    }
    Ok(())
}
