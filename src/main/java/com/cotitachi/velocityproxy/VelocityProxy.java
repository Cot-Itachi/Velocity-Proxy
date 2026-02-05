package com.cotitachi.velocityproxy;

import com.cotitachi.velocityproxy.command.AltsCommand;
import com.cotitachi.velocityproxy.command.FriendCommand;
import com.cotitachi.velocityproxy.command.ModerationCommand;
import com.cotitachi.velocityproxy.command.PartyCommand;
import com.cotitachi.velocityproxy.database.DatabasePool;
import com.cotitachi.velocityproxy.listener.AltDetectionListener;
import com.cotitachi.velocityproxy.listener.PartyListener;
import com.cotitachi.velocityproxy.listener.PlayerListener;
import com.cotitachi.velocityproxy.listener.PunishmentListener;
import com.cotitachi.velocityproxy.manager.AltDetectionManager;
import com.cotitachi.velocityproxy.manager.CooldownManager;
import com.cotitachi.velocityproxy.manager.FriendManager;
import com.cotitachi.velocityproxy.manager.PartyManager;
import com.cotitachi.velocityproxy.manager.PunishmentManager;
import com.cotitachi.velocityproxy.manager.UUIDCache;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "velocityproxy",
    name = "VelocityProxy",
    version = "1.0",
    authors = {"Cot_Itachi"}
)
public class VelocityProxy {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataFolder;
    
    private DatabasePool database;
    private UUIDCache uuidCache;
    private FriendManager friendManager;
    private PartyManager partyManager;
    private AltDetectionManager altDetectionManager;
    private PunishmentManager punishmentManager;
    private CooldownManager cooldownManager;

    @Inject
    public VelocityProxy(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        try {
            this.database = new DatabasePool(dataFolder);
            this.uuidCache = new UUIDCache(database);
            this.friendManager = new FriendManager(database, server, uuidCache);
            this.partyManager = new PartyManager(database, server, uuidCache);
            this.altDetectionManager = new AltDetectionManager(database, server, uuidCache);
            this.punishmentManager = new PunishmentManager(database, server, uuidCache, altDetectionManager);
            this.cooldownManager = new CooldownManager();

            CommandManager cm = server.getCommandManager();
            
            FriendCommand friendCmd = new FriendCommand(this);
            cm.register(cm.metaBuilder("friend").aliases("f").build(), friendCmd.create());
            
            PartyCommand partyCmd = new PartyCommand(this);
            cm.register(cm.metaBuilder("party").aliases("p").build(), partyCmd.create());
            cm.register(cm.metaBuilder("pc").build(), partyCmd.createChatAlias());

            AltsCommand altsCmd = new AltsCommand(this);
            cm.register(cm.metaBuilder("alts").build(), altsCmd.create());
            cm.register(cm.metaBuilder("ipwhitelist").build(), altsCmd.createWhitelist());
            cm.register(cm.metaBuilder("altalerts").build(), altsCmd.createAlerts());

            ModerationCommand modCmd = new ModerationCommand(this);
            cm.register(cm.metaBuilder("kick").build(), modCmd.createKick());
            cm.register(cm.metaBuilder("ban").build(), modCmd.createBan());
            cm.register(cm.metaBuilder("tempban").build(), modCmd.createTempban());
            cm.register(cm.metaBuilder("unban").build(), modCmd.createUnban());
            cm.register(cm.metaBuilder("mute").build(), modCmd.createMute());
            cm.register(cm.metaBuilder("tempmute").build(), modCmd.createTempmute());
            cm.register(cm.metaBuilder("unmute").build(), modCmd.createUnmute());
            cm.register(cm.metaBuilder("warn").build(), modCmd.createWarn());
            cm.register(cm.metaBuilder("unwarn").build(), modCmd.createUnwarn());
            cm.register(cm.metaBuilder("history").build(), modCmd.createHistory());
            cm.register(cm.metaBuilder("ipban").build(), modCmd.createIpban());
            cm.register(cm.metaBuilder("unipban").build(), modCmd.createUnipban());
            cm.register(cm.metaBuilder("staffchat").aliases("sc").build(), modCmd.createStaffChat());
            cm.register(cm.metaBuilder("broadcast").build(), modCmd.createBroadcast());
            cm.register(cm.metaBuilder("find").build(), modCmd.createFind());
            cm.register(cm.metaBuilder("alert").build(), modCmd.createAlert());

            server.getEventManager().register(this, new PlayerListener(friendManager, uuidCache));
            server.getEventManager().register(this, new PartyListener(partyManager));
            server.getEventManager().register(this, new AltDetectionListener(altDetectionManager));
            server.getEventManager().register(this, new PunishmentListener(punishmentManager));

            server.getScheduler()
                .buildTask(this, cooldownManager::cleanup)
                .repeat(5, TimeUnit.MINUTES)
                .schedule();

            server.getScheduler()
                .buildTask(this, altDetectionManager::cleanOldData)
                .repeat(24, TimeUnit.HOURS)
                .schedule();

            server.getScheduler()
                .buildTask(this, punishmentManager::checkExpirations)
                .repeat(1, TimeUnit.MINUTES)
                .schedule();

            logger.info("Loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize", e);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.shutdown();
        }
        logger.info("Shutdown complete");
    }

    public ProxyServer getServer() { 
        return server; 
    }
    
    public DatabasePool getDatabase() { 
        return database; 
    }
    
    public FriendManager getFriendManager() { 
        return friendManager; 
    }
    
    public PartyManager getPartyManager() { 
        return partyManager; 
    }

    public AltDetectionManager getAltDetectionManager() {
        return altDetectionManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }
    
    public CooldownManager getCooldownManager() { 
        return cooldownManager; 
    }
    
    public UUIDCache getUuidCache() { 
        return uuidCache; 
    }
}