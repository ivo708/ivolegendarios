package name.modid.mixin;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.brigadier.context.CommandContext;

import org.spongepowered.asm.mixin.Shadow;

import name.modid.Ivolegendarios;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mixin(Entity.class)
public abstract class BlockDisplayMixin {
	private static ServerBossBar bossBar;
	private static double lastX;
	private static double lastY;
	private static double lastZ;
    private static boolean dentro = false;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onEntityTick(CallbackInfo ci) {
        if ((Object)this instanceof ArmorStandEntity) {
            NbtCompound nbt = new NbtCompound();
            Entity self = (Entity) (Object)this;
            if (self.hasCustomName() && "Portal Hoopa".equals(self.getCustomName().getString())) {
                //Ivolegendarios.LOGGER.info("Portal Hoopa detectado");
                Box boundingBox = this.getBoundingBox();
                List<PlayerEntity> players = this.getWorld().getEntitiesByClass(PlayerEntity.class, boundingBox, player -> true);
                if (!players.isEmpty()) {
                    handlePlayerCollisionHoopa(players.get(0));
                }
            }
            if (self.hasCustomName() && "Portal Hoopa Vuelta".equals(self.getCustomName().getString())) {
	    	  //Ivolegendarios.LOGGER.info("HoopaVuelta");
	          Box boundingBox = this.getBoundingBox();
		  		RegistryKey<World> voidWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("server", "void"));
				World voidWorld = self.getServer().getWorld(voidWorldKey);
	          List<PlayerEntity> players = voidWorld.getEntitiesByClass(PlayerEntity.class, boundingBox, player -> true);
	          if (!players.isEmpty()) {
	          	handlePlayerCollisionHoopaVuelta(players.get(0));
	          }
	      }
      
	  }
  }

    private void handlePlayerCollisionHoopa(PlayerEntity player) {
    	if(!isDentro()) {
	    	this.lastX=this.getX();
	    	this.lastY=this.getY();
	    	this.lastZ=this.getZ();
	    	setDentro(true);
	        String borrar="execute positioned "+this.getX()+" "+this.getY()+" "+this.getZ()+" run kill @e[type=minecraft:block_display,distance=..5]";
	        String borrarStand="execute positioned "+this.getX()+" "+this.getY()+" "+this.getZ()+" run kill @e[type=minecraft:armor_stand,distance=..5]";
	        Ivolegendarios.LOGGER.info("COORDS ISLA:"+" "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+Ivolegendarios.COORDS_ISLA[2]);
	        String tp="execute in server:void run tp "+player.getName().getLiteralString()+" "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+(Ivolegendarios.COORDS_ISLA[2]-3)+" 180 0";
	        String objAdd="scoreboard objectives add jugadorPortalLegendarios dummy";
	        String objRmv="scoreboard objectives remove jugadorPortalLegendarios";
	        String randomPokemon=Ivolegendarios.POKEMON_LIST.get(Ivolegendarios.gen-1).get((int) (Math.random()*Ivolegendarios.POKEMON_LIST.get(Ivolegendarios.gen-1).size()));
		    Ivolegendarios.LOGGER.info("SPAWNEANDO A "+randomPokemon);
	        String summon="execute in server:void run pokespawnat "+Ivolegendarios.COORDS_SPAWN[0]+" "+Ivolegendarios.COORDS_SPAWN[1]+" "+Ivolegendarios.COORDS_SPAWN[2]+" "+randomPokemon+" lvl=50 no_ai";
	        double random=Math.random()*Ivolegendarios.SHINY_RATE;
	        if(Ivolegendarios.capturado) {
	        	random=random/10;
	        }
	        if(random<=1) {
	        	summon=summon+" shiny";
	        }
	        showBossBar(player);
	        String objSet="scoreboard players set "+player.getName().getLiteralString()+" jugadorPortalLegendarios 1";
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), objRmv);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), objAdd);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), objSet);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), tp);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), summon);
	    	spawnPortalVuelta(player);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), borrarStand);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), borrar);
            if (Ivolegendarios.scheduler != null && !Ivolegendarios.scheduler.isShutdown()) {
    	    	Ivolegendarios.scheduler.shutdownNow();
            }
	    	Ivolegendarios.scheduler = Executors.newSingleThreadScheduledExecutor();
	        Ivolegendarios.scheduler.schedule(() -> {
	        	handlePlayerCollisionHoopaVuelta(player);
	        }, 10, TimeUnit.MINUTES);
    	}
    }
    
    private void handlePlayerCollisionHoopaVuelta(PlayerEntity player) {
    	if(isDentro()) {
    	    Ivolegendarios.LOGGER.info("TP VUELTA");
	    	BlockPos blockPos = new BlockPos((int) Ivolegendarios.COORDS_SPAWN[0], (int) 319, (int) Ivolegendarios.COORDS_SPAWN[2]);
		    int chunkX = MathHelper.floor(Ivolegendarios.COORDS_SPAWN[0]) >> 4;
		    int chunkZ = MathHelper.floor(Ivolegendarios.COORDS_SPAWN[2]) >> 4;
		    player.getServer().getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
			int groundLevelY = Objects.requireNonNull(player.getServer().getOverworld()).getTopPosition(Heightmap.Type.WORLD_SURFACE, blockPos).getY();
		    player.getServer().getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
	        String tp="execute in server:void run execute positioned "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+Ivolegendarios.COORDS_ISLA[2]+" run execute as @a[distance=..100] run execute in minecraft:overworld run tp @s "+this.lastX+" "+(this.lastY-25)+" "+this.lastZ;
	        String kill="execute in server:void run execute positioned "+Ivolegendarios.COORDS_SPAWN[0]+" "+Ivolegendarios.COORDS_SPAWN[1]+" "+Ivolegendarios.COORDS_SPAWN[2]+" run tp @e[type=cobblemon:pokemon,distance=..100] ~ ~-100 ~";
	        String objAdd="scoreboard objectives add jugadorPortalLegendarios dummy";
	        String objRmv="scoreboard objectives remove jugadorPortalLegendarios";
	        String borrar="execute in server:void run execute positioned "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+Ivolegendarios.COORDS_ISLA[2]+" run kill @e[type=minecraft:block_display,distance=..5]";
	        String borrarStand="execute in server:void run execute positioned "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+Ivolegendarios.COORDS_ISLA[2]+" run kill @e[type=minecraft:armor_stand,distance=..5]";
			RegistryKey<World> voidWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("server", "void"));
			World voidWorld = player.getServer().getWorld(voidWorldKey);
			if (voidWorld == null) {
				Ivolegendarios.LOGGER.info("ES NULL");
			}
			voidWorld.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
	        bossBar.clearPlayers();
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), objRmv);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), objAdd);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), kill);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), borrarStand);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), borrar);
	        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), tp);


	        setDentro(false);
            if (Ivolegendarios.scheduler != null && !Ivolegendarios.scheduler.isShutdown()) {
    	    	Ivolegendarios.scheduler.shutdownNow();
            }
	    	Ivolegendarios.scheduler = Executors.newSingleThreadScheduledExecutor();
	    	Ivolegendarios.scheduler.schedule(() -> {
	        	Ivolegendarios.spawnTry(player.getServer());
	        }, Ivolegendarios.TIME_INTERVAL, TimeUnit.SECONDS);
    	}
    }
    
	private void spawnPortalVuelta(PlayerEntity player) { 
		RegistryKey<World> voidWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("server", "void"));
		World voidWorld = player.getServer().getWorld(voidWorldKey);
		if (voidWorld == null) {
			Ivolegendarios.LOGGER.info("ES NULL");
		}
		ArmorStandEntity stand = new ArmorStandEntity(voidWorld, Ivolegendarios.COORDS_ISLA[0], Ivolegendarios.COORDS_ISLA[1], Ivolegendarios.COORDS_ISLA[2]);
	    stand.setInvisible(true);     // hacerlo invisible
	    stand.setInvulnerable(true);    // para que no se destruya fácilmente
	    stand.setNoGravity(true);       // sin gravedad (opcional)
	    stand.setCustomName(Text.literal("Portal Hoopa Vuelta"));
	    stand.setCustomNameVisible(false);
	    Box box = new Box(Ivolegendarios.COORDS_ISLA[0] - 2, Ivolegendarios.COORDS_ISLA[1] -2, Ivolegendarios.COORDS_ISLA[2], Ivolegendarios.COORDS_ISLA[0] + 2, Ivolegendarios.COORDS_ISLA[1] + 2, Ivolegendarios.COORDS_ISLA[2] + 1);
	    stand.setBoundingBox(box);
	    // Añadimos la entidad al mundo
	    int chunkX = MathHelper.floor(Ivolegendarios.COORDS_ISLA[0]) >> 4;
	    int chunkZ = MathHelper.floor(Ivolegendarios.COORDS_ISLA[2]) >> 4;
	    ChunkPos pos = new ChunkPos(chunkX, chunkZ);
		String comandoPortal="execute in server:void run summon block_display "+Ivolegendarios.COORDS_ISLA[0]+" "+Ivolegendarios.COORDS_ISLA[1]+" "+Ivolegendarios.COORDS_ISLA[2]+" {Passengers:[{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.875f,0f,0f,-1.7624f,0f,0.5133f,0f,1.6092f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.8111121335f,0.1197111713f,0f,-1.7606f,-0.4852547164f,0.446797007f,0f,2.1191f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.6237702106f,0.2389868482f,0f,-1.6405f,-0.9375515948f,0.413907594f,0f,2.5634f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.3258f,0.3434f,0f,-1.4029f,-1.3258f,0.3434f,0f,2.9752f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.9375967492f,0.4206144708f,0f,-1.0615f,-1.6237441368f,0.2428749404f,0f,3.3158f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.4852793964f,0.4627985893f,0f,-0.6409f,-1.8111055207f,0.124005265f,0f,3.5583f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0f,0.4791f,0f,-0.1789f,-1.875f,0f,0f,3.6821f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.4852547149f,0.4467970068f,0f,0.2973f,-1.8111121329f,-0.119711171f,0f,3.682f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.3258f,0.3434f,0f,1.1533f,-1.3258f,-0.3434f,0f,3.3244f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.6237441368f,0.2428749393f,0f,1.4939f,-0.9375967447f,-0.4206144708f,0f,2.983f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.8111055207f,0.124005265f,0f,1.7364f,-0.4852793962f,-0.4627985893f,0f,2.5623f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.875f,0f,0f,1.8603f,0f,-0.4791f,0f,2.1004f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.8111121329f,-0.1197111713f,0f,1.8599f,0.4852547164f,-0.4467970068f,0f,1.6256f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.6237702096f,-0.2389868482f,0f,1.7398f,0.9375515948f,-0.4139075937f,0f,1.1813f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.3258f,-0.3434f,0f,1.5022f,1.3258f,-0.3434f,0f,0.7696f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.9375967447f,-0.4206144708f,0f,1.1608f,1.6237441368f,-0.2428749393f,0f,0.429f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.4852793959f,-0.4627985893f,0f,0.7402f,1.8111055207f,-0.1240052649f,0f,0.1865f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0f,-0.4791f,0f,0.2782f,1.875f,0f,0f,0.0626f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.4852547174f,-0.4467970068f,0f,-0.204f,1.8111121329f,0.1197111716f,0f,0.0643f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.9375515961f,-0.4139075937f,0f,-0.6483f,1.6237702096f,0.2389868485f,0f,0.1843f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.3258f,-0.3434f,0f,-1.0601f,1.3258f,0.3434f,0f,0.4219f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.6237441404f,-0.2428749393f,0f,-1.4007f,0.9375967447f,0.4206144717f,0f,0.7633f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.8111055208f,-0.124005265f,0f,-1.6432f,0.4852793962f,0.4627985893f,0f,1.184f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.9375515948f,0.4139075937f,0f,0.7415f,-1.6237702096f,-0.2389868482f,0f,3.562f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5f,0f,0f,-0.1875f,0f,0.1875f,0f,3.6875f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,-0.67f,0.1439033891f,0.1811110924f,0f,3.553125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,-0.09375f,0f,-1.08f,0.278f,0.1623797632f,0f,3.3025f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,-0.1325825215f,0f,-1.4375f,0.3931513703f,0.1325825215f,0f,2.9375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0f,-0.1875f,0f,-1.75f,0.5f,0f,0f,1.625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,-0.67f,0.1439033891f,0.1811110924f,0f,3.553125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,-0.1623797632f,0f,-1.683125f,0.4815101245f,0.09375f,0f,2.5f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,-0.1820564092f,0f,-1.76125f,0.5398579386f,0.0448521336f,0f,2.068125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5f,0f,0f,-0.1875f,0f,0.1875f,0f,-0.125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0f,-0.1875f,0f,2.036875f,0.5f,0f,0f,1.625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,0.048528571f,0f,0.25f,-0.1439033891f,0.1811110924f,0f,3.696875f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,0.09375f,0f,0.715625f,-0.278f,0.1623797632f,0f,3.580625f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,0.1325825215f,0f,1.16125f,-0.3931513703f,0.1325825215f,0f,3.330625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,0.1623797632f,0f,1.521875f,-0.4815101245f,0.09375f,0f,2.98125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,0.1820564092f,0f,1.745f,-0.5398579386f,0.0448521336f,0f,2.608125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,0.048528571f,0f,-0.71875f,-0.1439033891f,0.1811110924f,0f,0f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,0.09375f,0f,-1.17375f,-0.278f,0.1623797632f,0f,0.269375f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,0.1325825215f,0f,-1.57f,-0.3931513703f,0.1325825215f,0f,0.664375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,0.1623797632f,0f,-1.845625f,-0.4815101245f,0.09375f,0f,1.140625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,0.1820564092f,0f,-1.943125f,-0.5398579386f,0.0448521336f,0f,1.62125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,0.29875f,0.1439033891f,0.1811110924f,0f,-0.14375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,-0.09375f,0f,0.809375f,0.278f,0.1623797632f,0f,-0.00875f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,-0.1325825215f,0f,1.29375f,0.3931513703f,0.1325825215f,0f,0.27125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,-0.1623797632f,0f,1.684375f,0.4815101245f,0.09375f,0f,0.659375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,-0.1820564092f,0f,1.926875f,0.5398579386f,0.0448521336f,0f,1.08125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]}],Tags:[\"PortalHoopaVuelta\"]}";
		voidWorld.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
		voidWorld.spawnEntity(stand);
		player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), comandoPortal);
		Ivolegendarios.LOGGER.info("SPAWN STAND VUELTA");
	}
	private void showBossBar(PlayerEntity player) {
	    ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
	    // 10 minutos = 600 segundos = 600*20 ticks
	    int TOTAL_TICKS = 600 * 20;
	    final int[] tickCount = {0}; // Contenedor mutable

	    bossBar = new ServerBossBar(
	            Text.literal("Tiempo restante"),
	            BossBar.Color.RED,
	            BossBar.Style.byName("SOLID")
	    );
	    bossBar.setPercent(1.0f); // 100%

	    if (serverPlayer != null) {
	        bossBar.addPlayer(serverPlayer);
	    }

	    ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
	    	if(dentro) {
		        tickCount[0]++;
		        float progress = 1.0f - ((float) tickCount[0] / TOTAL_TICKS);
		        bossBar.setPercent(progress);
		        if (tickCount[0] >= TOTAL_TICKS) {
		            bossBar.removePlayer(serverPlayer);
		            tickCount[0] = 0;
		        }
	    	}
	    });
	}

    private static synchronized boolean isDentro() {
        Ivolegendarios.LOGGER.info("Valor actual de 'dentro': " + dentro);
        return dentro;
    }

    private static synchronized  void setDentro(boolean value) {
        Ivolegendarios.LOGGER.info("Estableciendo 'dentro' a: " + value);
        dentro = value;
    }
    @Shadow
    protected abstract void writeCustomDataToNbt(NbtCompound nbt);
    @Shadow
    protected abstract double getX();
    @Shadow
    protected abstract double getY();
    @Shadow
    protected abstract double getZ();
    @Shadow
    protected abstract Box getBoundingBox();
    @Shadow
    protected abstract World getWorld();
    
    
    
    
}
