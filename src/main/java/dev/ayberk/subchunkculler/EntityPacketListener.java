package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class EntityPacketListener extends PacketListenerAbstract {

    private final Main plugin;

    public EntityPacketListener(Main plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.getConfigManager().isEntityCullerEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // BUG-6 FIX: bypass permission check
        if (player.hasPermission("subchunkculler.bypass")) {
            return;
        }

        var packetType = event.getPacketType();
        ConfigManager config = plugin.getConfigManager();

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        UUID viewerUUID = player.getUniqueId();
        Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewerUUID);
        if (viewerSectionY == null) {
            viewerSectionY = player.getLocation().getBlockY() >> 4;
        }
        int cutoffBlockY = config.computeCutoffSection(viewerSectionY) << 4;

        // Block entity spawn packets below cutoff
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            double entityY = packet.getPosition().getY();

            if (entityY < cutoffBlockY) {
                event.setCancelled(true);
            }
        }
        // BUG-4 FIX: Removed per-packet reflection for sound dampening.
        // Sound dampening is low-value and the reflection overhead per sound packet
        // was destroying Netty thread performance. Removed entirely.
    }
}
