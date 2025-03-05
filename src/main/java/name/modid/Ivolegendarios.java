package name.modid;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.decoration.ArmorStandEntity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.literal;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class Ivolegendarios implements ModInitializer {
	public static final String MOD_ID = "ivolegendarios";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	
	private final static Random random = new Random();


	private static final int[][] intervalos= {
			{1,143},
			{152,242},
			{252,376},
			{387,478},
			{495,637},
			{650,715},
			{722,784},
			{810,887},
			{906,1000}
	};
	private static final String CONFIG_FILE_NAME = "config.json";
	private JsonObject configData;
	public static int gen;
	public static boolean capturado;

	// Config properties
    public static ScheduledExecutorService scheduler;
    public static ScheduledExecutorService scheduler2;
	public static float SHINY_RATE;
	private static float SPAWN_RATE;
	private static float SPAWN_TRYS;
	public static List<List<String>> POKEMON_LIST;
	public static long TIME_INTERVAL = 30 * 60; // 30 minutes in ticks (20 ticks per second)
	public static long PORTAL_DURATION_INTERVAL = 3 * 60;	
	public static int[] COORDS_ISLA;
	public static int[] COORDS_SPAWN;

		@Override
	public void onInitialize() {
		LOGGER.info("Initializing ivolegendarios...");

		// Load config on mod startup
		loadConfig();
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
        	LOGGER.info("LEGENDARIO REGISTRADO");
        	
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
            	spawnTry(server);
            }, TIME_INTERVAL, TimeUnit.SECONDS);
        });
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
		    dispatcher.register(CommandManager.literal("ivolegendarios")
	                .then(literal("reload")
		                .executes(context -> {
		                	loadConfig();
		                    return 1;
		                })
		            )
		    );
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
		    dispatcher.register(CommandManager.literal("ivolegendarios")
	                .then(literal("changeparams")
	    		            .then(CommandManager.argument("SPAWN_RATE", IntegerArgumentType.integer(0))
	    	    		            .then(CommandManager.argument("TIME_INTERVAL", IntegerArgumentType.integer(0))
	    	    	    		            .then(CommandManager.argument("DURACION_BUFO", IntegerArgumentType.integer(0))
								                .executes(context -> {
								                	SPAWN_RATE = IntegerArgumentType.getInteger(context, "SPAWN_RATE");
								                	TIME_INTERVAL = IntegerArgumentType.getInteger(context, "TIME_INTERVAL");
								                	int DURACION_BUFO = IntegerArgumentType.getInteger(context, "DURACION_BUFO");
								                    scheduler2 = Executors.newSingleThreadScheduledExecutor();
								                    scheduler2.schedule(() -> {
								                    	loadConfig();
								                    	scheduler2.shutdownNow();
								                    }, DURACION_BUFO, TimeUnit.SECONDS);
								                    return 1;
								                })
						                )
	    	    		            )
	    		            )
	    		     )
		    );
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
		    dispatcher.register(CommandManager.literal("ivolegendarios")
		        .then(CommandManager.literal("tryspawn")
		            // Caso sin jugador, se ejecuta la acción por defecto.
		            .executes(context -> {
		                if (scheduler != null && !scheduler.isShutdown()) {
		                    scheduler.shutdownNow();
		                }
		                scheduler = Executors.newSingleThreadScheduledExecutor();
		                scheduler.schedule(() -> {
		                    spawnPortal(context.getSource().getServer(),null);
		                }, 1, TimeUnit.SECONDS);
		                return 1;
		            })
		            .then(CommandManager.argument("player", EntityArgumentType.player())
		                .executes(context -> {
		                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
		                    if (scheduler != null && !scheduler.isShutdown()) {
		                        scheduler.shutdownNow();
		                    }
		                    scheduler = Executors.newSingleThreadScheduledExecutor();
		                    scheduler.schedule(() -> {
		                        // Se asume que existe una sobrecarga o lógica para spawnPortal con jugador.
		                        spawnPortal(context.getSource().getServer(), player);
		                    }, 1, TimeUnit.SECONDS);
		                    return 1;
		                })
		            )
		        )
		    );
		});

        // Al detener el servidor, se apaga el scheduler para liberar recursos
        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        });
	}

	private void loadConfig() {
		LOGGER.info("Loading ivolegendarios config");

		// Get the config directory within the server's folder (using server's directory)
		Path configDir = Path.of("config", MOD_ID);  // Access server's "config" folder
		File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

		// Check if the config file exists, otherwise create it with default values
		if (!configFile.exists()) {
			try {
				configDir.toFile().mkdirs(); // Ensure the directory exists
				configFile.createNewFile();
				LOGGER.info("Config file not found. Creating a new one with default values.");
				createDefaultConfig(configFile); // Create the default config if it doesn't exist
			} catch (IOException e) {
				LOGGER.error("Could not create config file", e);
			}
		}

		// Now load the config file
		try (FileReader reader = new FileReader(configFile)) {
		    Gson gson = new Gson();
		    configData = gson.fromJson(reader, JsonObject.class);
		    
		    if (configData == null) {
		        LOGGER.info("Config file is empty or invalid. Creating default config.");
		        createDefaultConfig(configFile);
		        // Vuelve a cargar la configuración
		        try (FileReader reader2 = new FileReader(configFile)) {
		            configData = gson.fromJson(reader2, JsonObject.class);
		        }
		    }

		    TIME_INTERVAL = configData.has("spawnTimer") ? configData.get("spawnTimer").getAsLong() : 10L;
			PORTAL_DURATION_INTERVAL = configData.has("portalDuration") ? configData.get("portalDuration").getAsLong() : 10L;
			SHINY_RATE = configData.has("shinyRateInverse") ? configData.get("shinyRateInverse").getAsFloat() : 1024.0f;
			SPAWN_RATE = configData.has("spawnRate") ? configData.get("spawnRate").getAsFloat() : 10.0f;
			SPAWN_TRYS = configData.has("spawnTrys") ? configData.get("spawnTrys").getAsInt() : 10;
			JsonArray coordsJson = configData.has("coordsIsla") ? configData.getAsJsonArray("coordsIsla") : null;
			if (coordsJson != null && coordsJson.size() > 0) {
			    COORDS_ISLA = new int[coordsJson.size()];
			    for (int i = 0; i < coordsJson.size(); i++) {
			        COORDS_ISLA[i] = coordsJson.get(i).getAsInt();
			    }
			}
			JsonArray coordsSpawnJson = configData.has("coordsSpawn") ? configData.getAsJsonArray("coordsSpawn") : null;
			if (coordsSpawnJson != null && coordsSpawnJson.size() > 0) {
			    COORDS_SPAWN = new int[coordsSpawnJson.size()];
			    for (int i = 0; i < coordsSpawnJson.size(); i++) {
			    	COORDS_SPAWN[i] = coordsSpawnJson.get(i).getAsInt();
			    }
			}
			POKEMON_LIST = gson.fromJson(configData.getAsJsonArray("PokemonList"), new com.google.gson.reflect.TypeToken<List<List<String>>>(){}.getType());
		    LOGGER.info("LOADED LEGENDARY SPAWN CONFIG: {} {} {} {} {} {}",
		            String.valueOf(TIME_INTERVAL),
		            String.valueOf(SPAWN_RATE),
		            String.format("%.6f", SHINY_RATE),
		            Arrays.toString(COORDS_ISLA),
		            Arrays.toString(COORDS_SPAWN),
		            POKEMON_LIST.toString());
		} catch (IOException e) {
		    LOGGER.error("Error reading config file", e);
		}
	}

	// Function to create the default config file
	private void createDefaultConfig(File configFile) {
		JsonObject defaultConfig = new JsonObject();

		// Set default values
		defaultConfig.addProperty("spawnTimer", 1800);
		defaultConfig.addProperty("portalDuration", 180);
		defaultConfig.addProperty("shinyRateInverse", 8192);  // Store the inverse of shiny rate
		defaultConfig.addProperty("spawnRate", 10.0);
		defaultConfig.addProperty("spawnTrys", 10);
		JsonArray coordsIsla = new JsonArray();
		coordsIsla.add(164);
		coordsIsla.add(135);
		coordsIsla.add(-945);

		defaultConfig.add("coordsIsla",coordsIsla);

		JsonArray coordsSpawn = new JsonArray();
		coordsSpawn.add(159);
		coordsSpawn.add(131);
		coordsSpawn.add(-977);

		defaultConfig.add("coordsSpawn",coordsSpawn);
		// Set default Pokémon list (this can be modified as needed)
		JsonArray pokemonArray = new JsonArray();
		// Generación I (Kanto)
		JsonArray pokemonArray1 = new JsonArray();
		pokemonArray1.add("Articuno");
		pokemonArray1.add("Zapdos");
		pokemonArray1.add("Moltres");
		//pokemonArray1.add("Mewtwo");
		//pokemonArray1.add("Mew"); // Mítico

		// Generación II (Johto)
		JsonArray pokemonArray2 = new JsonArray();
		pokemonArray2.add("Raikou");
		pokemonArray2.add("Entei");
		pokemonArray2.add("Suicune");
		pokemonArray2.add("Lugia");
		pokemonArray2.add("Ho-Oh");
		//pokemonArray2.add("Celebi"); // Mítico

		// Generación III (Hoenn)
		JsonArray pokemonArray3 = new JsonArray();
		//pokemonArray3.add("Regirock");
		//pokemonArray3.add("Regice");
		//pokemonArray3.add("Registeel");
		pokemonArray3.add("Latias");
		pokemonArray3.add("Latios");
		pokemonArray3.add("Kyogre");
		pokemonArray3.add("Groudon");
		//pokemonArray3.add("Rayquaza");
		//pokemonArray3.add("Deoxys"); // Mítico

		// Generación IV (Sinnoh)
		JsonArray pokemonArray4 = new JsonArray();
		pokemonArray4.add("Uxie");
		pokemonArray4.add("Mesprit");
		pokemonArray4.add("Azelf");
		//pokemonArray4.add("Dialga");
		//pokemonArray4.add("Palkia");
		pokemonArray4.add("Heatran");
		//pokemonArray4.add("Regigigas");
		//pokemonArray4.add("Giratina");
		//pokemonArray4.add("Cresselia");
		//pokemonArray4.add("Darkrai"); // Mítico
		//pokemonArray4.add("Shaymin"); // Mítico
		//pokemonArray4.add("Arceus"); // Mítico

		// Generación V (Unova)
		JsonArray pokemonArray5 = new JsonArray();
		pokemonArray5.add("Victini"); // Mítico
		pokemonArray5.add("Cobalion");
		pokemonArray5.add("Terrakion");
		pokemonArray5.add("Virizion");
		pokemonArray5.add("Tornadus");
		pokemonArray5.add("Thundurus");
		//pokemonArray5.add("Reshiram");
		//pokemonArray5.add("Zekrom");
		pokemonArray5.add("Landorus");
		pokemonArray5.add("Keldeo"); // Mítico
		//pokemonArray5.add("Meloetta"); // Mítico

		// Generación VI (Kalos)
		JsonArray pokemonArray6 = new JsonArray();
		//pokemonArray6.add("Xerneas");
		//pokemonArray6.add("Yveltal");
		pokemonArray6.add("Zygarde");
		pokemonArray6.add("Diancie");   // Mítico
		//pokemonArray6.add("Hoopa");      // Mítico
		pokemonArray6.add("Volcanion");  // Mítico

		// Generación VII (Alola)
		JsonArray pokemonArray7 = new JsonArray();
		pokemonArray7.add("TapuKoko");
		pokemonArray7.add("TapuLele");
		pokemonArray7.add("TapuBulu");
		pokemonArray7.add("TapuFini");
		/*pokemonArray7.add("Cosmog");
		pokemonArray7.add("Cosmoem");
		pokemonArray7.add("Solgaleo");
		pokemonArray7.add("Lunala");*/
		pokemonArray7.add("Necrozma");
		pokemonArray7.add("Magearna");  // Mítico
		pokemonArray7.add("Marshadow"); // Mítico
		pokemonArray7.add("Zeraora");   // Mítico

		// Generación VIII (Galar)
		JsonArray pokemonArray8 = new JsonArray();
		pokemonArray8.add("Zacian");
		pokemonArray8.add("Zamazenta");
		pokemonArray8.add("Eternatus");
		pokemonArray8.add("Calyrex");
		//pokemonArray8.add("Regieleki");
		//pokemonArray8.add("Regidrago");
		pokemonArray8.add("Glastrier");
		pokemonArray8.add("Spectrier");

		// Generación IX (Paldea)
		JsonArray pokemonArray9 = new JsonArray();
		pokemonArray9.add("Koraidon");
		pokemonArray9.add("Miraidon");
		pokemonArray9.add("Chi-Yu");
		pokemonArray9.add("Chien-Pao");
		pokemonArray9.add("Ting-Lu");
		pokemonArray9.add("Wo-Chien");
		//pokemonArray9.add("Ogerpon");
		pokemonArray9.add("Terapagos");
		pokemonArray9.add("Pecharunt");
		
		
		pokemonArray.add(pokemonArray1);
		pokemonArray.add(pokemonArray2);
		pokemonArray.add(pokemonArray3);
		pokemonArray.add(pokemonArray4);
		pokemonArray.add(pokemonArray5);
		pokemonArray.add(pokemonArray6);
		pokemonArray.add(pokemonArray7);
		pokemonArray.add(pokemonArray8);
		pokemonArray.add(pokemonArray9);

		defaultConfig.add("PokemonList", pokemonArray);

		// Write the default config to the file
		try (FileWriter writer = new FileWriter(configFile)) {
			Gson gson = new Gson();
			gson.toJson(defaultConfig, writer);
			LOGGER.info("Default config written to file.");
		} catch (IOException e) {
			LOGGER.error("Error writing default config file", e);
		}
	}
	public static void spawnTry(MinecraftServer server) {
    	LOGGER.info("SPAWN TRY");
		if (server != null) {
			if (Math.random()*100 <= SPAWN_RATE) {
				spawnPortal(server,null);
			}
			else {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdownNow();
                }
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.schedule(() -> {
                	spawnTry(server);
                }, TIME_INTERVAL, TimeUnit.SECONDS);
			}
		}			
	}
	private static void spawnPortal(MinecraftServer server,ServerPlayerEntity player) {
		if (server != null) {
	    	LOGGER.info("SPAWN PORTAL");
				List<ServerPlayerEntity>  playerList = server.getOverworld().getPlayers();
				if (!playerList.isEmpty()) {
					gen = random.nextInt(9)+1;
					int pokedexGen=0;
					int trys=0;
					// Select a random player
					ServerPlayerEntity randomPlayer = null;
	    	        LOGGER.info("PRE WHILE");
	                ServerCommandSource consoleSource = server.getCommandSource();
					double offsetX;
					double offsetZ;
	                if(player==null) {
						while(trys<(playerList.size()*(SPAWN_TRYS/100)) && pokedexGen==0) {
			    	        LOGGER.info("TRY:"+trys+1);
			    	        LOGGER.info("COMPROBANDO GEN: "+gen);
			    	        LOGGER.info("COMPROBANDO INTERVALO: "+intervalos[gen-1][0]+"-"+intervalos[gen-1][1]);
							randomPlayer = playerList.get(random.nextInt(playerList.size()));
			                String command1 = String.format("scoreboard players set "+randomPlayer.getName().getLiteralString()+" testPokedex 0");
			    	        //String command2 = "execute as "+randomPlayer.getName().toString()+" run ivopokedex intervalo "+intervalos[gen][0]+" "+intervalos[gen][1];
			                String command2 ="execute as "+randomPlayer.getName().getLiteralString()+" run ivopokedex intervalo "+intervalos[gen-1][0]+" "+intervalos[gen-1][1]+" visto";
			    	        LOGGER.info("Comando="+command2);		    	      
			    	        server.getCommandManager().executeWithPrefix(consoleSource, command1);
			    	        server.getCommandManager().executeWithPrefix(consoleSource, command2);
			                Scoreboard scoreboard = server.getScoreboard();
			                ScoreboardObjective objective = scoreboard.getNullableObjective("testPokedex");
			            	ReadableScoreboardScore scoreHolder = scoreboard.getScore(randomPlayer, objective);
			            	pokedexGen=scoreHolder.getScore();		  
			    	        server.getCommandManager().executeWithPrefix(consoleSource, command1);
			    	        trys++;
						}
						offsetX = random.nextInt(481) -240;
						offsetZ = random.nextInt(481) -240;
	                }
	                else {
	                	randomPlayer=player;
	                	pokedexGen=1;
	                	offsetX = random.nextInt(81) -40;
						offsetZ = random.nextInt(81) -40;	                	
	                }
					offsetX=offsetX+Math.copySign(10, offsetX);
					offsetZ=offsetZ+Math.copySign(10, offsetZ);
			    	LOGGER.info("POKEDEXGEN="+pokedexGen+" playernull="+(randomPlayer==null));
					if(randomPlayer!=null && pokedexGen==1) {
		                String command3 ="execute as "+randomPlayer.getName().getLiteralString()+" run ivopokedex intervalo "+intervalos[gen-1][0]+" "+intervalos[gen-1][1]+"";
		                String command4 = String.format("scoreboard players set "+randomPlayer.getName().getLiteralString()+" testPokedex 0");
		    	        server.getCommandManager().executeWithPrefix(consoleSource, command4);
		                server.getCommandManager().executeWithPrefix(consoleSource, command3);
		                Scoreboard scoreboard = server.getScoreboard();
		                ScoreboardObjective objective = scoreboard.getNullableObjective("testPokedex");
		            	ReadableScoreboardScore scoreHolder = scoreboard.getScore(randomPlayer, objective);
		            	pokedexGen=scoreHolder.getScore();
		            	if(pokedexGen==1) {
		            		capturado=true;
		            	}
		            	else {
		            		capturado=false;
		            	}
		    	        server.getCommandManager().executeWithPrefix(consoleSource, command4);
						Vec3d playerPos = randomPlayer.getPos();	

	
						double newX = playerPos.x + offsetX;
						double newZ = playerPos.z + offsetZ;
					    int chunkX = MathHelper.floor(newX) >> 4;
					    int chunkZ = MathHelper.floor(newZ) >> 4;
					    server.getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
		    	        LOGGER.info("PRE BLOCKPOS");
						BlockPos blockPos = new BlockPos((int) newX, (int) 319, (int) newZ);
						int groundLevelY = Objects.requireNonNull(server.getWorld(randomPlayer.getWorld().getRegistryKey()))
							    .getTopPosition(Heightmap.Type.WORLD_SURFACE, blockPos).getY();
	
						double newY = groundLevelY + 25;
						LOGGER.info("SPAWNMESSAGE");					
						String spawnMessage ="tellraw @a [{\"text\":\"\"},{\"text\":\"\\nSe ha avistado un portal cerca de [\",\"bold\":true,\"italic\":true,\"color\":\"yellow\"},{\"text\":\""+Math.round(newX)+"\",\"bold\":true,\"italic\":true,\"color\":\"dark_purple\"},{\"text\":\"] [\",\"bold\":true,\"italic\":true,\"color\":\"yellow\"},{\"text\":\""+Math.round(newZ)+"\",\"bold\":true,\"italic\":true,\"color\":\"dark_purple\"},{\"text\":\"]\\n\",\"bold\":true,\"italic\":true,\"color\":\"yellow\"}]";
		    	        server.getCommandManager().executeWithPrefix(server.getCommandSource(), spawnMessage);
						
						String comandoPortal="summon block_display "+newX+" "+newY+" "+newZ+" {Passengers:[{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.875f,0f,0f,-1.7624f,0f,0.5133f,0f,1.6092f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.8111121335f,0.1197111713f,0f,-1.7606f,-0.4852547164f,0.446797007f,0f,2.1191f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.6237702106f,0.2389868482f,0f,-1.6405f,-0.9375515948f,0.413907594f,0f,2.5634f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.3258f,0.3434f,0f,-1.4029f,-1.3258f,0.3434f,0f,2.9752f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.9375967492f,0.4206144708f,0f,-1.0615f,-1.6237441368f,0.2428749404f,0f,3.3158f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.4852793964f,0.4627985893f,0f,-0.6409f,-1.8111055207f,0.124005265f,0f,3.5583f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0f,0.4791f,0f,-0.1789f,-1.875f,0f,0f,3.6821f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.4852547149f,0.4467970068f,0f,0.2973f,-1.8111121329f,-0.119711171f,0f,3.682f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.3258f,0.3434f,0f,1.1533f,-1.3258f,-0.3434f,0f,3.3244f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.6237441368f,0.2428749393f,0f,1.4939f,-0.9375967447f,-0.4206144708f,0f,2.983f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.8111055207f,0.124005265f,0f,1.7364f,-0.4852793962f,-0.4627985893f,0f,2.5623f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.875f,0f,0f,1.8603f,0f,-0.4791f,0f,2.1004f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.8111121329f,-0.1197111713f,0f,1.8599f,0.4852547164f,-0.4467970068f,0f,1.6256f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.6237702096f,-0.2389868482f,0f,1.7398f,0.9375515948f,-0.4139075937f,0f,1.1813f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-1.3258f,-0.3434f,0f,1.5022f,1.3258f,-0.3434f,0f,0.7696f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.9375967447f,-0.4206144708f,0f,1.1608f,1.6237441368f,-0.2428749393f,0f,0.429f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.4852793959f,-0.4627985893f,0f,0.7402f,1.8111055207f,-0.1240052649f,0f,0.1865f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0f,-0.4791f,0f,0.2782f,1.875f,0f,0f,0.0626f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.4852547174f,-0.4467970068f,0f,-0.204f,1.8111121329f,0.1197111716f,0f,0.0643f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[0.9375515961f,-0.4139075937f,0f,-0.6483f,1.6237702096f,0.2389868485f,0f,0.1843f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.3258f,-0.3434f,0f,-1.0601f,1.3258f,0.3434f,0f,0.4219f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.6237441404f,-0.2428749393f,0f,-1.4007f,0.9375967447f,0.4206144717f,0f,0.7633f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[1.8111055208f,-0.124005265f,0f,-1.6432f,0.4852793962f,0.4627985893f,0f,1.184f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:nether_portal\",Properties:{axis:\"x\"}},transformation:[-0.9375515948f,0.4139075937f,0f,0.7415f,-1.6237702096f,-0.2389868482f,0f,3.562f,0f,0f,0.0547f,0f,0f,0f,0f,1f],brightness:{sky:15,block:12}},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5f,0f,0f,-0.1875f,0f,0.1875f,0f,3.6875f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,-0.67f,0.1439033891f,0.1811110924f,0f,3.553125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,-0.09375f,0f,-1.08f,0.278f,0.1623797632f,0f,3.3025f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,-0.1325825215f,0f,-1.4375f,0.3931513703f,0.1325825215f,0f,2.9375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0f,-0.1875f,0f,-1.75f,0.5f,0f,0f,1.625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,-0.67f,0.1439033891f,0.1811110924f,0f,3.553125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,-0.1623797632f,0f,-1.683125f,0.4815101245f,0.09375f,0f,2.5f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,-0.1820564092f,0f,-1.76125f,0.5398579386f,0.0448521336f,0f,2.068125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5f,0f,0f,-0.1875f,0f,0.1875f,0f,-0.125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0f,-0.1875f,0f,2.036875f,0.5f,0f,0f,1.625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,0.048528571f,0f,0.25f,-0.1439033891f,0.1811110924f,0f,3.696875f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,0.09375f,0f,0.715625f,-0.278f,0.1623797632f,0f,3.580625f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,0.1325825215f,0f,1.16125f,-0.3931513703f,0.1325825215f,0f,3.330625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,0.1623797632f,0f,1.521875f,-0.4815101245f,0.09375f,0f,2.98125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,0.1820564092f,0f,1.745f,-0.5398579386f,0.0448521336f,0f,2.608125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,0.048528571f,0f,-0.71875f,-0.1439033891f,0.1811110924f,0f,0f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,0.09375f,0f,-1.17375f,-0.278f,0.1623797632f,0f,0.269375f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,0.1325825215f,0f,-1.57f,-0.3931513703f,0.1325825215f,0f,0.664375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,0.1623797632f,0f,-1.845625f,-0.4815101245f,0.09375f,0f,1.140625f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,0.1820564092f,0f,-1.943125f,-0.5398579386f,0.0448521336f,0f,1.62125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.5370547594f,-0.048528571f,0f,0.29875f,0.1439033891f,0.1811110924f,0f,-0.14375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.4815101245f,-0.09375f,0f,0.809375f,0.278f,0.1623797632f,0f,-0.00875f,0f,0f,0.1875f,-0.05875f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.3931513703f,-0.1325825215f,0f,1.29375f,0.3931513703f,0.1325825215f,0f,0.27125f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.278f,-0.1623797632f,0f,1.684375f,0.4815101245f,0.09375f,0f,0.659375f,0f,0f,0.1875f,-0.0625f,0f,0f,0f,1f]},{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:gold_block\",Properties:{}},transformation:[0.1330015267f,-0.1820564092f,0f,1.926875f,0.5398579386f,0.0448521336f,0f,1.08125f,0f,0f,0.1875f,-0.061875f,0f,0f,0f,1f]}],Tags:[\"PortalHoopa\"]}";
						ArmorStandEntity stand = new ArmorStandEntity(server.getOverworld(), newX, newY, newZ);
					    stand.setInvisible(true);     // hacerlo invisible
					    stand.setInvulnerable(true);    // para que no se destruya fácilmente
					    stand.setNoGravity(true);       // sin gravedad (opcional)
					    stand.setCustomName(Text.literal("Portal Hoopa"));
					    stand.setCustomNameVisible(false);
					    Box box = new Box(newX - 2, newY -2, newZ, newX + 2, newY + 2, newZ + 1);
					    stand.setBoundingBox(box);					    
					    // Añadimos la entidad al mundo

						server.execute(() -> {
						    server.getOverworld().spawnEntity(stand);
			    	        LOGGER.info("SPAWN STAND");

						});
						server.execute(() -> {
						    server.getCommandManager().executeWithPrefix(server.getCommandSource(), comandoPortal);
			    	        LOGGER.info("SPAWN PORTAL");
						});
					    server.getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
					    //server.getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
	                    if (scheduler != null && !scheduler.isShutdown()) {
	                        scheduler.shutdownNow();
	                    }
	                    scheduler = Executors.newSingleThreadScheduledExecutor();
	                    scheduler.schedule(() -> {
	                    	killPortal(server,newX,newY,newZ);
	                    }, 3, TimeUnit.MINUTES);
						//generar el pokemon tambien en el otro mundo, a saber como
	
						//y ya veras como detectar que un jugador interacciona con el portal o se acerca
					}
					else {
	                    if (scheduler != null && !scheduler.isShutdown()) {
	                        scheduler.shutdownNow();
	                    }
	                    scheduler = Executors.newSingleThreadScheduledExecutor();
	                    scheduler.schedule(() -> {
	                    	spawnTry(server);
	                    }, TIME_INTERVAL, TimeUnit.SECONDS);
						
					}
			}
		}
	}
	public static void killPortal(MinecraftServer server, double x,double y,double z) {
		server.execute(() -> {
	    int chunkX = MathHelper.floor(x) >> 4;
	    int chunkZ = MathHelper.floor(z) >> 4;
	    server.getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        String borrar="execute positioned "+x+" "+y+" "+z+" run kill @e[type=minecraft:block_display,distance=..5]";
        String borrarStand="execute positioned "+x+" "+y+" "+z+" run kill @e[type=minecraft:armor_stand,distance=..5]";
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), borrar);
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), borrarStand);
	    server.getOverworld().getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
        	spawnTry(server);
        }, TIME_INTERVAL, TimeUnit.SECONDS);
		});
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}