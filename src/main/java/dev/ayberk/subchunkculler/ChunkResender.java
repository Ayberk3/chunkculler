package dev.ayberk.subchunkculler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class ChunkResender {

    private static final Logger LOGGER = Logger.getLogger("SubChunkCuller");
    private static Method getHandlePlayer;
    private static Field connectionField;
    private static Method sendPacketMethod;
    private static Method getHandleWorld;
    private static Method getChunkMethod;
    private static Method getLightEngineMethod;
    private static Constructor<?> chunkPacketConstructor;
    private static boolean initialized = false;
    private static boolean failed = false;

    public static synchronized void init() {
        if (initialized || failed) {
            return;
        }

        try {
            String cbPackage = Bukkit.getServer().getClass().getPackage().getName();

            // 1. CraftPlayer -> getHandle() -> ServerPlayer
            Class<?> craftPlayerClass = Class.forName(cbPackage + ".entity.CraftPlayer");
            getHandlePlayer = craftPlayerClass.getMethod("getHandle");
            Class<?> serverPlayerClass = getHandlePlayer.getReturnType();

            // 2. ServerPlayer -> connection
            for (Field f : serverPlayerClass.getFields()) {
                if (f.getName().equals("connection") || f.getName().equals("playerConnection") || f.getType().getSimpleName().contains("Connection")) {
                    connectionField = f;
                    break;
                }
            }
            if (connectionField == null) {
                for (Field f : serverPlayerClass.getDeclaredFields()) {
                    if (f.getType().getSimpleName().contains("Connection") || f.getName().equals("connection")) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
            }

            if (connectionField != null) {
                Class<?> connectionClass = connectionField.getType();
                for (Method m : connectionClass.getMethods()) {
                    if ((m.getName().equals("send") || m.getName().equals("sendPacket") || m.getName().equals("sendPacketImmediately"))
                            && m.getParameterCount() == 1) {
                        sendPacketMethod = m;
                        break;
                    }
                }
            }

            // 3. CraftWorld -> getHandle() -> ServerLevel
            Class<?> craftWorldClass = Class.forName(cbPackage + ".CraftWorld");
            getHandleWorld = craftWorldClass.getMethod("getHandle");
            Class<?> serverLevelClass = getHandleWorld.getReturnType();

            // Prefer getChunkIfLoaded to avoid any disk I/O on main thread
            for (Method m : serverLevelClass.getMethods()) {
                if ((m.getName().equals("getChunkIfLoaded") || m.getName().equals("getChunkIfLoadedImmediately"))
                        && m.getParameterCount() == 2 && m.getParameterTypes()[0] == int.class) {
                    getChunkMethod = m;
                    break;
                }
            }
            if (getChunkMethod == null) {
                for (Method m : serverLevelClass.getMethods()) {
                    if (m.getName().equals("getChunk") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == int.class) {
                        getChunkMethod = m;
                        break;
                    }
                }
            }

            for (Method m : serverLevelClass.getMethods()) {
                if (m.getName().equals("getLightEngine")) {
                    getLightEngineMethod = m;
                    break;
                }
            }

            // 4. Packet class: ClientboundLevelChunkWithLightPacket
            Class<?> packetClass = null;
            try {
                packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
            } catch (ClassNotFoundException e) {
                try {
                    packetClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutMapChunk");
                } catch (ClassNotFoundException ignored) {
                }
            }

            if (packetClass != null) {
                for (Constructor<?> c : packetClass.getConstructors()) {
                    if (c.getParameterCount() == 4 || c.getParameterCount() == 2 || c.getParameterCount() == 1) {
                        chunkPacketConstructor = c;
                        break;
                    }
                }
            }

            if (getHandlePlayer != null && connectionField != null && sendPacketMethod != null
                    && getHandleWorld != null && getChunkMethod != null && chunkPacketConstructor != null) {
                initialized = true;
                LOGGER.info("ChunkResender initialized successfully (Native NMS Packet Delivery Active).");
            } else {
                failed = true;
                LOGGER.warning("ChunkResender could not find all NMS methods; falling back to standard Bukkit chunk loading.");
            }
        } catch (Throwable t) {
            failed = true;
            LOGGER.warning("ChunkResender initialization failed: " + t.getMessage());
        }
    }

    public static boolean resendChunk(Player player, World world, int cx, int cz) {
        if (!initialized && !failed) {
            init();
        }

        if (!initialized || player == null || !player.isOnline() || world == null) {
            return false;
        }

        try {
            Object nmsPlayer = getHandlePlayer.invoke(player);
            Object connection = connectionField.get(nmsPlayer);
            Object nmsWorld = getHandleWorld.invoke(world);
            Object nmsChunk = getChunkMethod.invoke(nmsWorld, cx, cz);
            if (nmsChunk == null) {
                return false;
            }

            Object lightEngine = getLightEngineMethod != null ? getLightEngineMethod.invoke(nmsWorld) : null;

            Object packet;
            int paramCount = chunkPacketConstructor.getParameterCount();
            if (paramCount == 4) {
                packet = chunkPacketConstructor.newInstance(nmsChunk, lightEngine, null, null);
            } else if (paramCount == 2) {
                packet = chunkPacketConstructor.newInstance(nmsChunk, lightEngine);
            } else {
                packet = chunkPacketConstructor.newInstance(nmsChunk);
            }

            sendPacketMethod.invoke(connection, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
