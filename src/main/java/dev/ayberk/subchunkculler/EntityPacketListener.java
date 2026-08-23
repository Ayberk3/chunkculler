package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
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

        if (player.hasPermission("subchunkculler.bypass")) {
            return;
        }

        UUID viewerUUID = player.getUniqueId();
        var packetType = event.getPacketType();
        EntityTrackingManager tracking = plugin.getEntityTrackingManager();

        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            int entityId = packet.getEntityId();
            double entityY = packet.getPosition().getY();
            double viewerY = player.getLocation().getY();

            if ((viewerY - entityY) >= plugin.getConfigManager().getHideDistanceY()) {
                tracking.hideEntity(player, player.getWorld().getEntities().stream()
                        .filter(e -> e.getEntityId() == entityId)
                        .findFirst().orElse(null));
                event.setCancelled(true);
                return;
            }
        }

        if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook packet = new WrapperPlayServerEntityHeadLook(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity packet = new WrapperPlayServerEntityVelocity(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_ANIMATION) {
            WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.SET_PASSENGERS) {
            WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (plugin.getConfigManager().isDampenUndergroundSounds() && packetType == PacketType.Play.Server.SOUND_EFFECT) {
            try {
                WrapperPlayServerSoundEffect soundPacket = new WrapperPlayServerSoundEffect(event);
                Vector3i pos = soundPacket.getPosition();
                if (pos != null) {
                    double soundY = pos.getY();
                    double viewerY = player.getLocation().getY();
                    if ((viewerY - soundY) >= plugin.getConfigManager().getHideDistanceY()) {
                        event.setCancelled(true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
