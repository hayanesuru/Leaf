package org.dreeam.leaf.async;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public final class AsyncPlayerDataSaving {

    public static final ThreadPoolExecutor IO_POOL;
    private static final Tasks PLAYER_DATA = new Tasks("playerData", true);
    private static final Tasks ADVANCEMENTS = new Tasks("advancements", true);
    private static final Tasks STATS = new Tasks("stats", true);
    private static final Tasks LEVEL_DATA = new Tasks("levelData", false);
    private static final Tasks USER_LIST = new Tasks("userList", false);
    private static final Tasks PROFILE_CACHE = new Tasks("profileCache", false);

    private static final Logger LOGGER = LogManager.getLogger("Leaf Async IO");

    private AsyncPlayerDataSaving() {
    }

    private record Tasks(Object2ObjectMap<String, Future<Void>> map, String ty, boolean stripPath) {
        private Tasks(String ty, boolean stripPath) {
            this(Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>()), ty, stripPath);
        }

        private String format(String path) {
            String name = FilenameUtils.getBaseName(path);
            if (name == null || name.isEmpty()) {
                return null;
            } else {
                return stripPath() ? name : path;
            }
        }
    }

    static {
        IO_POOL = new ThreadPoolExecutor(
            1,
            1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new com.google.common.util.concurrent.ThreadFactoryBuilder()
                .setPriority(Thread.NORM_PRIORITY - 2)
                .setNameFormat("Leaf IO Thread")
                .setUncaughtExceptionHandler(Util::onThreadException)
                .build(),
            new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    public static void submit(Callable<?> task, Path path, int ty) {
        submit(task, path, ty, true);
    }

    public static void submit(Callable<?> task, Path path, int ty, boolean async) {
        submit(task, path == null ? null : path.toString(), ty, async);
    }

    public static void submit(Callable<?> task, String path, int ty) {
        submit(task, path, ty, true);
    }

    private static void submit(Callable<?> task, String path, int ty, boolean async) {
        if (ty == 0) {
            submit(task, path, PLAYER_DATA, async, AsyncPlayerDataSave.playerdata);
        } else if (ty == 1) {
            submit(task, path, ADVANCEMENTS, async, AsyncPlayerDataSave.advancements);
        } else if (ty == 2) {
            submit(task, path, STATS, async, AsyncPlayerDataSave.stats);
        } else if (ty == 3) {
            submit(task, path, LEVEL_DATA, async, AsyncPlayerDataSave.levelData);
        } else if (ty == 4) {
            submit(task, path, USER_LIST, async, AsyncPlayerDataSave.userList);
        } else if (ty == 5) {
            submit(task, path, PROFILE_CACHE, async, AsyncPlayerDataSave.profileCache);
        }
    }

    private static void submit(Callable<?> task, String path, Tasks tasks, boolean async, boolean enabled) {
        String name = tasks.format(path);
        if (name == null) {
            return;
        }
        if (enabled) {
            Future<?> fut = tasks.map().get(name);
            if (fut != null) {
                try {
                    fut.get();
                    tasks.map().remove(name, fut);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LOGGER.error("Failed to save {} for {}", tasks.ty(), fut, e.getCause());
                }
            }
        }
        if (task == null) {
            return;
        }
        if (!enabled || !async) {
            try {
                runDirectly(task);
            } catch (Exception e) {
                LOGGER.error("Failed to save {} for {}", tasks.ty(), name, e);
            }
        } else {
            tasks.map().put(name, IO_POOL.submit(new SaveTask(name, task, tasks)));
        }
    }

    private record SaveTask(String name,
                            Callable<?> task,
                            Tasks tasks) implements Callable<Void> {
        @Override
        public Void call() {
            try {
                task.call();
            } catch (Exception e) {
                LOGGER.error("Failed to save {} for {}", tasks.ty(), this, e);
            } finally {
                tasks.map().remove(name);
            }
            return null;
        }

        @Override
        public @NotNull String toString() {
            return "SaveTask{name='" + name + "', type='" + tasks.ty() + "'}";
        }
    }

    private static void runDirectly(Callable<?> callable) throws Exception {
        callable.call();
    }

    public static Path tempFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String prefix = FilenameUtils.getBaseName(fileName);
        String suffix = FilenameUtils.getExtension(fileName);
        return tempFile(file.getParent(), prefix, suffix);
    }

    public static Path tempFile(Path dir, String prefix, String suffix) throws IOException {
        return Files.createTempFile(dir, prefix + '-', suffix);
    }
}
