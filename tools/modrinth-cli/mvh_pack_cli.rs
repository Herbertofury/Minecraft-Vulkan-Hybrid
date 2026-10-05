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
    if arg=="create-cumulative" {
      let ledger=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative/instance.json");
      if ledger.exists(){return Err("Cumulative instance already recorded; preserve it".into());}
      theseus::EventState::init().await?;theseus::State::init_mvh_cli("ModrinthApp".to_string()).await?;
      let source="local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3";
      let original=theseus::instance::get(source).await?.ok_or("Missing owned vanilla base")?;
      let original_path=theseus::instance::get_full_path(source).await?;
      if !original.instance.name.starts_with("MVH isolated ")||!original_path.join("MVH-BENCHMARK-IDENTITY.json").exists(){return Err("Unowned source profile".into());}
      let job=theseus::install::duplicate_instance(source.into()).await?;let job_id=job.job_id.parse()?;
      loop {let current=theseus::install::get_job(job_id).await?;match current.status{
       InstallJobStatus::Succeeded=>{
        let id=current.instance_id.ok_or("Clone has no instance ID")?;let root=theseus::instance::get_full_path(&id).await?;
        theseus::instance::edit(&id,serde_json::from_value(serde_json::json!({"name":"MVH isolated Noxviola cumulative"}))?).await?;
        let marker=serde_json::json!({"task":"mvh-cumulative-20261005","id":id,"source":source});std::fs::write(root.join("MVH-CUMULATIVE-IDENTITY.json"),serde_json::to_vec_pretty(&marker)?)?;
        std::fs::create_dir_all(ledger.parent().unwrap())?;std::fs::write(&ledger,serde_json::to_vec_pretty(&serde_json::json!({"id":id,"path":root,"name":"MVH isolated Noxviola cumulative"}))?)?;println!("CREATED_CUMULATIVE {}",id);return Ok(());
       },InstallJobStatus::Failed|InstallJobStatus::Interrupted|InstallJobStatus::Canceled=>return Err("Cumulative clone job failed; preserve diagnostics".into()),_=>tokio::time::sleep(Duration::from_millis(500)).await}
      }
    }
    if arg=="add-cumulative"||arg=="launch-cumulative"||arg=="launch-cumulative-profile"||arg=="launch-cumulative-zink"||arg=="launch-cumulative-zink-profile"||arg=="launch-cumulative-zink-export"||arg=="configure-cumulative" {
      let ledger:serde_json::Value=serde_json::from_slice(&std::fs::read("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative/instance.json")?)?;
      let id=ledger["id"].as_str().ok_or("Missing owned cumulative ID")?;
      eprintln!("MVH_PHASE cumulative_state_init");
      theseus::EventState::init().await?;tokio::time::timeout(Duration::from_secs(60),theseus::State::init_mvh_cli("ModrinthApp".to_string())).await??;
      eprintln!("MVH_PHASE cumulative_owner_validation");
      let root=theseus::instance::get_full_path(id).await?;let instance=theseus::instance::get(id).await?.ok_or("Unknown cumulative instance")?;
      let marker:serde_json::Value=serde_json::from_slice(&std::fs::read(root.join("MVH-CUMULATIVE-IDENTITY.json"))?)?;
      if marker["id"]!=id||marker["task"]!="mvh-cumulative-20261005"||instance.instance.name!="MVH isolated Noxviola cumulative"||root.file_name().unwrap()=="Noxviola"{return Err("Cumulative ownership mismatch".into());}
      if arg=="add-cumulative" {
        use sha2::Digest;
        let input=PathBuf::from(std::env::args().nth(2).ok_or("Stage input required")?).canonicalize()?;
        let stage_root=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative/stage-inputs").canonicalize()?;
        if !input.starts_with(stage_root){return Err("Stage input outside task folder".into());}
        let plan:serde_json::Value=serde_json::from_slice(&std::fs::read(input)?)?;
        theseus::instance::sync_content_files(id).await?;
        if let Some(disabled)=plan["disable"].as_array(){for name in disabled{let name=name.as_str().ok_or("Invalid filename")?;if !name.starts_with("mods/")||name.contains("..")||name.contains('\\'){return Err("Invalid owned mod path".into());}if root.join(name).exists()||root.join(format!("{}.disabled",name)).exists(){theseus::instance::toggle_disable_project(id,name,Some(false)).await?;}}}
        if let Some(enabled)=plan["enable"].as_array(){for name in enabled{let name=name.as_str().ok_or("Invalid filename")?;if !name.starts_with("mods/")||name.contains("..")||name.contains('\\'){return Err("Invalid owned mod path".into());}if root.join(name).exists()||root.join(format!("{}.disabled",name)).exists(){theseus::instance::toggle_disable_project(id,name,Some(true)).await?;}else{return Err("Missing owned mod to enable".into());}}}
        for row in plan["add"].as_array().ok_or("Missing additions")?{
          let source=PathBuf::from(row["path"].as_str().ok_or("Invalid source")?).canonicalize()?;
          let allowed=[PathBuf::from("C:/Users/Owner/AppData/Roaming/ModrinthApp/profiles/Noxviola/mods"),PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/inventory/noxviola"),PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/destination/compat"),PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/noxviola-control/build/libs"),PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/hari-dimensions-build/forge/build/libs")];
          if source.extension().and_then(|x|x.to_str())!=Some("jar")||!allowed.iter().any(|base|base.canonicalize().map(|approved|source.starts_with(approved)).unwrap_or(false)){return Err("Unreviewed mod source path".into());}
          let actual=format!("{:x}",sha2::Sha256::digest(std::fs::read(&source)?));if row["sha256"].as_str()!=Some(actual.as_str()){return Err("Mod source changed after inventory".into());}
          let target=theseus::instance::add_project_from_path(id,&source,None).await?;println!("ADDED_CUMULATIVE {}",target);
        }return Ok(());
      }
      let mut args=vec!["-Dorg.lwjgl.system.stackSize=1024".to_string()];
      if arg.starts_with("launch-cumulative-zink") {
        let workspace="C:/Users/Owner/Documents/Codex/2026-10-04/task-4";
        let bin=format!("{}/mesa-zink-install/bin",workspace);
        args.push(format!("-javaagent:{}/destination/compat/opengl-over-vulkan/build/mvh-zink-agent.jar",workspace));
        args.push(format!("-Dmvh.zink.library={}/opengl32.dll",bin));
      }
      if arg=="launch-cumulative-zink-export" {
        args.push("-Dmixin.debug.export=true".into());args.push("-Dmixin.debug.export.filter=net.minecraft.world.level.chunk.PalettedContainer".into());
      }
      if arg=="launch-cumulative-zink-profile" {
        let file="C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative/diagnostics/embeddium-zink-20261005.jfr";
        if std::path::Path::new(file).exists(){return Err("Preserve the prior cumulative profile".into());}
        std::fs::create_dir_all(std::path::Path::new(file).parent().unwrap())?;
        args.push(format!("-XX:StartFlightRecording=filename={},settings=C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/diagnostics/recording-settings.jfc,dumponexit=true",file));
      }
      if arg=="launch-cumulative-profile" {
        let label=std::env::args().nth(2).ok_or("Native diagnostic label required")?;
        if label.is_empty()||label.len()>120||!label.bytes().all(|c|c.is_ascii_alphanumeric()||c==b'-'||c==b'_'){return Err("Invalid native diagnostic label".into());}
        let directory=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative/diagnostics");
        std::fs::create_dir_all(&directory)?;
        let file=directory.join(format!("{}.jfr",label));
        if file.exists(){return Err("Preserve the prior native diagnostic".into());}
        args.push(format!("-XX:StartFlightRecording=filename={},settings=C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/diagnostics/recording-settings.jfc,dumponexit=true",file.display()));
      }
      let patch=serde_json::json!({"content_set_patch":{"loader_version":"47.4.26"},"launch_overrides":{"java_path":"C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/javaw.exe","extra_launch_args":args,"memory":{"maximum":8192},"force_fullscreen":false,"game_resolution":[1920,1080]}});
      eprintln!("MVH_PHASE cumulative_configuration");
      tokio::time::timeout(Duration::from_secs(60),theseus::instance::edit(id,serde_json::from_value(patch)?)).await??;
      eprintln!("MVH_PHASE cumulative_sync_isolation");
      for opt in theseus::data::InstanceSyncedOption::ALL{if opt.is_available(){tokio::time::timeout(Duration::from_secs(60),theseus::instance::set_synced_option(id,opt,false,None)).await??;}}
      if arg=="configure-cumulative"{println!("CONFIGURED_CUMULATIVE");return Ok(());}
      eprintln!("MVH_PHASE cumulative_launch");
      let process=tokio::time::timeout(Duration::from_secs(180),theseus::instance::run(id,theseus::instance::QuickPlayType::Singleplayer("MVH-Benchmark-20261004".into()))).await??;println!("PROCESS {}",process.uuid);theseus::process::wait_for(process.uuid).await?;println!("PROCESS_FINISHED");return Ok(());
    }
    if arg=="launch" || arg=="launch-profile" || arg=="add-grass" || arg=="toggle-grass" || arg=="install-resource-repairs" || arg=="metadata" || arg=="configure" || arg=="stop" || arg=="add-control" || arg=="disable-duplicates" || arg=="repair-pack" || arg=="update-test-tools" || arg=="repair-dimthread" || arg=="update-startup" || arg=="update-integration" || arg=="launch-zink" || arg=="launch-smoke" {
      let id=std::env::args().nth(2).ok_or("Expected isolated instance id")?;
      theseus::EventState::init().await?;
      theseus::State::init_mvh_cli("ModrinthApp".to_string()).await?;
      if arg=="metadata" {let m=theseus::metadata::mvh_refresh_forge_versions().await?;println!("{}",serde_json::to_string(&m)?);return Ok(());}
      if !["local:cdb476fb-b03b-4145-a054-17e041603944","local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3","local:b8b62592-bf53-4cc8-a770-a07757e1f41a","local:9ec14df5-d028-4fc0-865e-f4d898af4409"].contains(&id.as_str()){return Err("Only the four task-created benchmark instance IDs are accepted".into());}
      if arg=="install-resource-repairs" {
        if !["local:b8b62592-bf53-4cc8-a770-a07757e1f41a","local:9ec14df5-d028-4fc0-865e-f4d898af4409"].contains(&id.as_str()){return Err("Resource repairs require an owned full-pack clone".into());}
        let root=theseus::instance::get_full_path(&id).await?;let instance=theseus::instance::get(&id).await?.ok_or("Missing owned clone")?;
        if !instance.instance.name.starts_with("MVH isolated ")||!root.join("MVH-NOXVIOLA-IDENTITY.json").exists()||root.file_name().unwrap()=="Noxviola"{return Err("Resource-repair ownership mismatch".into());}
        use sha2::Digest;
        let source=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/development/resource-repairs-1.0.0/Hari-Noxviola-Resource-Repairs-1.0.0.zip");
        if format!("{:x}",sha2::Sha256::digest(std::fs::read(&source)?))!="a226452f9d9b76a8d9148a87648fb92fd54923c96cae2e65f520e98451db1338"{return Err("Resource overlay changed after verification".into());}
        let path=theseus::instance::add_project_from_path(&id,&source,Some(theseus::data::ProjectType::ResourcePack)).await?;println!("ADDED_PRIVATE_RESOURCE_OVERLAY {}",path);return Ok(());
      }
      if arg=="toggle-grass" {
        if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Only owned candidate accepts grass toggle".into());}
        let root=theseus::instance::get_full_path(&id).await?;let instance=theseus::instance::get(&id).await?.ok_or("Missing owned candidate")?;
        if !instance.instance.name.starts_with("MVH isolated ")||!root.join("MVH-NOXVIOLA-IDENTITY.json").exists()||root.file_name().unwrap()=="Noxviola"{return Err("Grass experiment ownership mismatch".into());}
        let enabled=match std::env::args().nth(3).as_deref(){Some("present")=>true,Some("absent")=>false,_=>return Err("Expected present or absent".into())};
        theseus::instance::sync_content_files(&id).await?;
        theseus::instance::toggle_disable_project(&id,"mods/mvh-grass-distance-compat-1.0.0.jar",Some(enabled)).await?;
        println!("GRASS_EXPERIMENT {}",if enabled{"present"}else{"absent"});return Ok(());
      }
      if arg=="add-grass" {if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Only owned candidate accepts grass experiment".into());}let path=theseus::instance::add_project_from_path(&id,&PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/destination/compat/grass-distance-sort/build/libs/mvh-grass-distance-compat-1.0.0.jar"),None).await?;println!("ADDED_GRASS {}",path);return Ok(());}
      if arg=="update-integration" {
        if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409" {return Err("Integration is limited to the isolated candidate".into());}
        theseus::instance::sync_content_files(&id).await?;
        for old in ["mods/HariMultiThread-Ultimate-1.20.1-2.4.10-vulkan-hybrid.jar","mods/dimthread-FORGE-mc1.20.1-v1.2.1-MVH-Hari-Owner-Interop-v6.jar","mods/harimt-forge-1.20.1-2.4.11-noxviola.1-dimensions-vulkan-hybrid-all.jar"] {
          theseus::instance::toggle_disable_project(&id,old,Some(false)).await?;
        }
        let source=PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/hari-dimensions-build/forge/build/libs/harimt-forge-1.20.1-2.4.11-noxviola.2-dimensions-vulkan-hybrid-all.jar");
        let path=theseus::instance::add_project_from_path(&id,&source,None).await?;
        println!("UPDATED_INTEGRATION {}",path);return Ok(());
      }
      if arg=="update-startup" {if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Only the isolated candidate accepts the startup experiment".into());}theseus::instance::sync_content_files(&id).await?;for old in ["mods/fieldguide-forge-1.20.1-1.17.0.jar","mods/mvh-fieldguide-query-compat-1.0.0.jar","mods/mvh-fieldguide-query-compat-1.1.0.jar"]{theseus::instance::toggle_disable_project(&id,old,Some(false)).await?;}for source in ["C:/Users/Owner/Documents/Codex/2026-10-04/task-4/inventory/noxviola/upstream/fieldguide-1.20.4+1.20.1-forge.jar","C:/Users/Owner/Documents/Codex/2026-10-04/task-4/destination/compat/fieldguide-query-split/build/libs/mvh-fieldguide-query-compat-1.1.1.jar"]{let path=theseus::instance::add_project_from_path(&id,&PathBuf::from(source),None).await?;println!("UPDATED_STARTUP {}",path);}return Ok(());}
      if arg=="repair-dimthread" {if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Only the isolated Hari candidate accepts its pinned DimThreads repair".into());}theseus::instance::toggle_disable_project(&id,"mods/dimthread-FORGE-mc1.20.1-v1.2.1-Per-Object-Interop-v5-PERF-FAILSAFE.jar",Some(false)).await?;let source=PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/inventory/noxviola/repairs/dimthread-FORGE-mc1.20.1-v1.2.1-MVH-Hari-Owner-Interop-v6.jar");let path=theseus::instance::add_project_from_path(&id,&source,None).await?;println!("REPAIRED_DIMTHREAD {}",path);return Ok(());}
      if arg=="update-test-tools" {theseus::instance::sync_content_files(&id).await?;let instance_root=theseus::instance::get_full_path(&id).await?;for old in ["mods/mvh-noxviola-control-1.0.0.jar","mods/mvh-noxviola-control-1.0.1.jar","mods/mvh-noxviola-control-1.0.2.jar","mods/create-automotives-1.20.1-V2-MVH-BoilerboxModels-v1.jar"]{if instance_root.join(old).exists() || instance_root.join(format!("{}.disabled",old)).exists(){theseus::instance::toggle_disable_project(&id,old,Some(false)).await?;}}for source in ["C:/Users/Owner/Documents/Codex/2026-10-04/task-4/noxviola-control/build/libs/mvh-noxviola-control-1.0.3.jar","C:/Users/Owner/Documents/Codex/2026-10-04/task-4/destination/compat/opengl-texture-lookup/build/libs/mvh-opengl-texture-compat-1.0.0.jar","C:/Users/Owner/Documents/Codex/2026-10-04/task-4/inventory/noxviola/repairs/create-automotives-1.20.1-V2-MVH-BoilerboxModels-v2.jar"]{let path=theseus::instance::add_project_from_path(&id,&PathBuf::from(source),None).await?;println!("UPDATED_TEST {}",path);}return Ok(());}
      if arg=="repair-pack" {for (old,new) in [("cataclysm-to-magic-fix-1.1.0.jar","cataclysm-to-magic-fix-1.1.0-MVH-TransformerReturn-v1.jar"),("create-automotives-1.20.1-V2.jar","create-automotives-1.20.1-V2-MVH-BoilerboxModels-v1.jar")]{theseus::instance::toggle_disable_project(&id,&format!("mods/{}",old),Some(false)).await?;let source=PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/inventory/noxviola/repairs").join(new);let path=theseus::instance::add_project_from_path(&id,&source,None).await?;println!("REPAIRED_PACK {}",path);}return Ok(());}
      if arg=="stop" {let processes=theseus::process::get_by_instance_id(&id).await?;for process in processes{theseus::process::kill(process.uuid).await?;println!("STOPPED_TASK_TEST {}",id);}return Ok(());}
      if arg=="add-control" {let source=PathBuf::from("C:/Users/Owner/Documents/Codex/2026-10-04/task-4/noxviola-control/build/libs/mvh-noxviola-control-1.0.0.jar");let path=theseus::instance::add_project_from_path(&id,&source,None).await?;println!("ADDED_CONTROL {}",path);return Ok(());}
      if arg=="disable-duplicates" {for name in ["mods/oculus-mc1.20.1-1.8.0.jar","mods/uranus-2.2.6-bugfix.2-1.20.1-forge.jar"]{let path=theseus::instance::toggle_disable_project(&id,name,Some(false)).await?;println!("DISABLED_DUPLICATE {}",path);}return Ok(());}
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
      let noxviola=id=="local:b8b62592-bf53-4cc8-a770-a07757e1f41a" || id=="local:9ec14df5-d028-4fc0-865e-f4d898af4409";
      let mut args:Vec<String>=if noxviola{vec!["-Dorg.lwjgl.system.stackSize=1024".into()]}else{vec!["-Dorg.lwjgl.system.stackSize=1024".into(),"-Dharimt.fps.auto=true".into(),"-Dharimt.fps.warmupSeconds=60".into()]};
      if arg=="launch-profile"{if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Only owned candidate accepts profiling".into());}let file="C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/noxviola/diagnostics/integrated-native-20261005.jfr";if std::path::Path::new(file).exists(){return Err("Preserve the prior profile before recording again".into());}args.push(format!("-XX:StartFlightRecording=filename={},settings=C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/diagnostics/recording-settings.jfc,dumponexit=true",file));}
      if arg=="launch-smoke" {if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409"{return Err("Smoke test requires the isolated candidate".into());}args.push("-Dmvh.pack.smoke=true".into());}
      if arg=="launch-zink" {
        if id!="local:9ec14df5-d028-4fc0-865e-f4d898af4409" {return Err("Zink test is limited to the isolated candidate".into());}
        let root="C:/Users/Owner/Documents/Codex/2026-10-04/task-4";
        args.push(format!("-javaagent:{}/destination/compat/opengl-over-vulkan/build/mvh-zink-agent.jar",root));
        args.push(format!("-Dmvh.zink.library={}/mesa-zink-install/bin/opengl32.dll",root));
        if std::env::var("GALLIUM_DRIVER").ok().as_deref()!=Some("zink") {return Err("Explicit child-process GALLIUM_DRIVER=zink required".into());}
      }
      let patch:theseus::prelude::EditInstance=serde_json::from_value(serde_json::json!({"content_set_patch":{"loader_version":"47.4.26"},"launch_overrides":{"java_path":"C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/javaw.exe","extra_launch_args":args,"memory":{"maximum":if noxviola{12288}else{8192}},"force_fullscreen":false,"game_resolution":[1920,1080]}}))?;
      theseus::instance::edit(&id,patch).await?;
      for opt in theseus::data::InstanceSyncedOption::ALL {if opt.is_available(){theseus::instance::set_synced_option(&id,opt,false,None).await?;}}
      if arg=="configure" {println!("CONFIGURED {}",id);return Ok(());}
      let proc=theseus::instance::run(&id,theseus::instance::QuickPlayType::Singleplayer(if noxviola{"MVH-Noxviola-Benchmark-20261004"}else{"MVH-Benchmark-20261004"}.to_string())).await?;
      println!("PROCESS {}",proc.uuid);
      theseus::process::wait_for(proc.uuid).await?;
      println!("PROCESS_FINISHED");
      return Ok(());
    }
    let pack=PathBuf::from(arg);
    let expected=PathBuf::from("C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/modrinth");
    if pack.parent()!=Some(expected.as_path()) || !["baseline-2.4.9.mrpack","candidate-2.4.10.mrpack","noxviola-baseline.mrpack","noxviola-candidate.mrpack"].contains(&pack.file_name().unwrap().to_string_lossy().as_ref()) {return Err("Only the four explicitly prepared isolated benchmark packs are accepted".into());}
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
