package org.dreeam.leaf.async.path;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.Util;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NullMarked
public final class AsyncPath {
    @Nullable
    private volatile Path ret = null;
    private boolean completion = false;
    @Nullable
    private PathNavigation listener;
    private final int accuracy;
    private PostProcessing postProcessing = NOP;

    private static final Path NULL = new Path();
    private static final PostProcessing NOP = new PostProcessing(ignore -> {}, null);

    public static final ThreadPoolExecutor PATH_PROCESSING_EXECUTOR = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new ThreadFactoryBuilder()
            .setPriority(Thread.NORM_PRIORITY)
            .setNameFormat("Leaf Async Pathfinding Thread")
            .setUncaughtExceptionHandler(Util::onThreadException)
            .build(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    record PostProcessing(Consumer<@Nullable Path> consumer, @Nullable PostProcessing next) {}

    public AsyncPath(int accuracy, Supplier<@Nullable Path> inner) {
        this.accuracy = accuracy;
        PATH_PROCESSING_EXECUTOR.execute(() -> {
            Path path = inner.get();
            ret = path != null ? path : NULL;
        });
    }

    public static void awaitProcessing(@Nullable Path path, Consumer<@Nullable Path> afterProcessing) {
        AsyncPath task = path != null ? path.task : null;
        if (task != null) {
            task.schedulePostProcessing(afterProcessing);
        } else {
            afterProcessing.accept(path);
        }
    }

    private void schedulePostProcessing(Consumer<@Nullable Path> runnable) {
        if (complete()) {
            Path result = ret;
            runnable.accept(result != NULL ? result : null);
        } else if (postProcessing == NOP) {
            postProcessing = new PostProcessing(runnable, null);
        } else {
            postProcessing = new PostProcessing(runnable, postProcessing);
        }
    }

    public boolean complete() {
        Path result = ret;
        if (!completion && result == null) {
            return false;
        } else {
            poll(result != NULL ? result : null);
            return true;
        }
    }

    private void poll(@Nullable Path result) {
        if (listener != null) {
            PathNavigation nav = listener;
            listener = null;
            if (result != null) {
                nav.path = result;
                nav.targetPos = result.getTarget();
                nav.reachRange = accuracy;
                nav.resetStuckTimeout();
            }
        }
        if (completion) {
            return;
        }
        completion = true;

        postProcessing.consumer.accept(result);
        PostProcessing next = postProcessing.next;
        while (next != null) {
            next.consumer.accept(result);
            next = next.next;
        }
        postProcessing = NOP;
    }

    public static void moveTo(PathNavigation nav, @Nullable Path path) {
        if (path == null) {
            nav.path = null;
        } else if (path.task == null || path.task.complete()) {
            nav.path = path.task != null ? path.task.ret : path;
        } else {
            path.task.listener = nav;
            if (nav.path != null) {
                nav.path.task = path.task;
            } else {
                nav.path = path;
            }
        }
    }

    public void stop() {
        listener = null;
        complete();
    }
}
