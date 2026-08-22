package com.sektor.verticalantiesp.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.sektor.verticalantiesp.VerticalAntiESP;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PacketManager extends PacketListenerAbstract {

    private final VerticalAntiESP plugin;

    public PacketManager(VerticalAntiESP plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Check bypass permission
        if (player.hasPermission("verticalantiesp.bypass")) {
            return;
        }

        UUID viewerUUID = player.getUniqueId();
        var packetType = event.getPacketType();

        // 1. Initial Spawn Filtering (If spawned already below delta Y)
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            int entityId = packet.getEntityId();
            double entityY = packet.getPosition().getY();
            double viewerY = player.getLocation().getY();

            if ((viewerY - entityY) >= plugin.getConfigManager().getHideDistanceY()) {
                // Add to hidden set and cancel spawn
                plugin.getTrackingManager().hideEntity(player, player.getWorld().getEntities().stream()
                        .filter(e -> e.getEntityId() == entityId)
                        .findFirst().orElse(null));
                event.setCancelled(true);
                return;
            }
        }

        // 2. Relative Move / Rotation
        if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }
        // 3. Teleport & Head Look
        else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook packet = new WrapperPlayServerEntityHeadLook(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }
        // 4. Metadata, Equipment & Velocity
        else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity packet = new WrapperPlayServerEntityVelocity(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }
        // 5. Animation, Status, Attributes & Passengers
        else if (packetType == PacketType.Play.Server.ENTITY_ANIMATION) {
            WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.SET_PASSENGERS) {
            WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
            if (plugin.getTrackingManager().isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }
        // 6. Underground Sound Dampening (Sound ESP Protection)
        else if (plugin.getConfigManager().isDampenUndergroundSounds() &&
                (packetType == PacketType.Play.Server.SOUND_EFFECT || packetType == PacketType.Play.Server.NAMED_SOUND_EFFECT)) {
            if (packetType == PacketType.Play.Server.SOUND_EFFECT) {
                WrapperPlayServerSoundEffect soundPacket = new WrapperPlayServerSoundEffect(event);
                double soundY = soundPacket.getFixedPosition().getY();
                double viewerY = player.getLocation().getY();
                if ((viewerY - soundY) >= plugin.getConfigManager().getHideDistanceY()) {
                    event.setCancelled(true);
                }
            } else {
                WrapperPlayServerNamedSoundEffect soundPacket = new WrapperPlayServerNamedSoundEffect(event);
                double soundY = soundPacket.getFixedPosition().getY();
                double viewerY = player.getLocation().getY();
                if ((viewerY - soundY) >= plugin.getConfigManager().getHideDistanceY()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
