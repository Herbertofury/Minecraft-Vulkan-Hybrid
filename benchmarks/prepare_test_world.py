from pathlib import Path
import struct,gzip,shutil,json,sqlite3
D=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks');P=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/profiles')
def S(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
 if t in (1,2,3,4,5,6):return struct.pack({1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[t],v)
 if t==8:return S(v)
 if t==9:
  tp,vs=v;return bytes([tp])+struct.pack('>i',len(vs))+b''.join(payload(tp,x) for x in vs)
 if t==10:return b''.join(bytes([tp])+S(k)+payload(tp,x) for k,(tp,x) in v.items())+b'\0'
 if t==11:return struct.pack('>i',len(v))+b''.join(struct.pack('>i',x) for x in v)
 raise ValueError(t)
def dimension(typ,settings,biome):return {'type':(8,'minecraft:'+typ),'generator':(10,{'type':(8,'minecraft:noise'),'settings':(8,'minecraft:'+settings),'biome_source':(10,biome)})}
dims={'minecraft:overworld':(10,dimension('overworld','overworld',{'type':(8,'minecraft:multi_noise'),'preset':(8,'minecraft:overworld')})),'minecraft:the_nether':(10,dimension('the_nether','nether',{'type':(8,'minecraft:multi_noise'),'preset':(8,'minecraft:nether')})),'minecraft:the_end':(10,dimension('the_end','end',{'type':(8,'minecraft:the_end')}))}
player={'Pos':(9,(6,[0.5,110.5,0.5])),'Rotation':(9,(5,[225.0,25.0])),'Motion':(9,(6,[0.,0.,0.])),'Dimension':(8,'minecraft:overworld'),'Health':(5,20.),'foodLevel':(3,20),'playerGameType':(3,1),'OnGround':(1,0),'abilities':(10,{'flying':(1,1),'mayfly':(1,1),'instabuild':(1,1),'invulnerable':(1,1),'mayBuild':(1,1),'flySpeed':(5,0.05),'walkSpeed':(5,0.1)})}
data={'DataVersion':(3,3465),'version':(3,19133),'Version':(10,{'Id':(3,3465),'Name':(8,'1.20.1'),'Snapshot':(1,0)}),'LevelName':(8,'MVH-Benchmark-20261004'),'GameType':(3,1),'allowCommands':(1,1),'hardcore':(1,0),'Difficulty':(1,2),'DifficultyLocked':(1,0),'SpawnX':(3,0),'SpawnY':(3,100),'SpawnZ':(3,0),'SpawnAngle':(5,225.),'Time':(4,0),'DayTime':(4,6000),'raining':(1,0),'thundering':(1,0),'rainTime':(3,999999),'thunderTime':(3,999999),'GameRules':(10,{'doDaylightCycle':(8,'false'),'doWeatherCycle':(8,'false'),'spawnRadius':(8,'0')}),'WorldGenSettings':(10,{'seed':(4,1729),'generate_features':(1,1),'bonus_chest':(1,0),'dimensions':(10,dims)}),'Player':(10,player),'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod:forge','mod:harimt','mod:mvhbench'])),'Disabled':(9,(8,[]))}),'enabled_features':(9,(8,['minecraft:vanilla']))}
initial=D/'initial-world';initial.mkdir(parents=True,exist_ok=True)
with gzip.open(initial/'level.dat','wb') as f:f.write(b'\x0a\0\0'+payload(10,{'Data':(10,data)}))
opts='''version:3465
fov:0.0
renderDistance:16
simulationDistance:12
entityDistanceScaling:1.0
maxFps:260
enableVsync:false
graphicsMode:1
ambientOcclusion:true
mipmapLevels:4
particles:0
biomeBlendRadius:2
fullscreen:false
pauseOnLostFocus:false
clouds:true
resourcePacks:[]
incompatibleResourcePacks:[]
'''
c=sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True)
records=[]
for iid,name,path in c.execute("SELECT id,name,path FROM instances WHERE name LIKE 'MVH isolated %'"):
 p=P/path;assert p.is_dir() and (p/'MVH-BENCHMARK-IDENTITY.json').exists()
 (p/'options.txt').write_text(opts)
 shutil.copy2('benchmark-companion/build/libs/mvh-benchmark-control-1.0.0.jar',p/'mods/mvh-benchmark-control-1.0.0.jar')
 world=p/'saves/MVH-Benchmark-20261004';assert not world.exists();shutil.copytree(initial,world)
 records.append({'id':iid,'name':name,'path':str(p)})
(D/'instances.json').write_text(json.dumps(records,indent=2));print(json.dumps(records,indent=2))
