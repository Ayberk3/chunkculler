package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
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
        ConfigManager config = plugin.getConfigManager();

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewerUUID);
        if (viewerSectionY == null) {
            viewerSectionY = player.getLocation().getBlockY() >> 4;
        }
        int cutoffBlockY = config.computeCutoffSection(viewerSectionY) << 4;

        // 1. Entity Spawn
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            int entityId = packet.getEntityId();
            double entityY = packet.getPosition().getY();

            if (entityY < cutoffBlockY) {
                tracking.hideEntityId(player, entityId);
                event.setCancelled(true);
                return;
            }
        }

        // 2. Entity Movements & Rotations
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
        }

        // 3. Metadata, Equipment & Attributes
        else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
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
        } else if (packetType == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }

        // 4. Status, Animations & Passengers
        else if (packetType == PacketType.Play.Server.ENTITY_ANIMATION) {
            WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Server.SET_PASSENGERS) {
            WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
            if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                event.setCancelled(true);
            }
        }

        // 5. Potion Effects (Entity Effect & Remove Effect)
        else if (packetType == PacketType.Play.Server.ENTITY_EFFECT) {
            try {
                WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(event);
                if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
            }
        } else if (packetType == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            try {
                WrapperPlayServerRemoveEntityEffect packet = new WrapperPlayServerRemoveEntityEffect(event);
                if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
            }
        }

        // 6. Hurt Animation & Damage Events (1.19.4+ / 1.20+)
        else if (packetType == PacketType.Play.Server.HURT_ANIMATION) {
            try {
                WrapperPlayServerHurtAnimation packet = new WrapperPlayServerHurtAnimation(event);
                if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
            }
        } else if (packetType == PacketType.Play.Server.DAMAGE_EVENT) {
            try {
                WrapperPlayServerDamageEvent packet = new WrapperPlayServerDamageEvent(event);
                if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
            }
        }

        // 7. Entity Attached Sound & World Sound ESP Dampener
        else if (packetType == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            try {
                WrapperPlayServerEntitySoundEffect packet = new WrapperPlayServerEntitySoundEffect(event);
                if (tracking.isEntityHidden(viewerUUID, packet.getEntityId())) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
            }
        } else if (plugin.getConfigManager().isDampenUndergroundSounds() && packetType == PacketType.Play.Server.SOUND_EFFECT) {
            try {
                WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
                java.lang.reflect.Method m = null;
                for (java.lang.reflect.Method method : sound.getClass().getMethods()) {
                    if (method.getName().equals("getEffectPosition") || method.getName().equals("getFixedPosition") || method.getName().equals("getPosition")) {
                        m = method;
                        break;
                    }
                }
                if (m != null) {
                    Object posObj = m.invoke(sound);
                    if (posObj != null) {
                        double soundY = 0;
                        if (posObj instanceof com.github.retrooper.packetevents.util.Vector3i v3i) {
                            soundY = v3i.getY();
                        } else if (posObj instanceof com.github.retrooper.packetevents.util.Vector3d v3d) {
                            soundY = v3d.getY();
                        }
                        if (soundY < cutoffBlockY) {
                            event.setCancelled(true);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
