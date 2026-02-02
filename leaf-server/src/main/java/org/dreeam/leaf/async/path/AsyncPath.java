package org.dreeam.leaf.async.path;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * I'll be using this to represent a path that not be processed yet!
 */
public final class AsyncPath extends Path implements Runnable {

    private boolean ready = false;

    /**
     * Runnable waiting for this to be processed
     * ConcurrentLinkedQueue is thread-safe, non-blocking and non-synchronized
     */
    private final ConcurrentLinkedQueue<Runnable> postProcessing = new ConcurrentLinkedQueue<>();

    /**
     * A list of positions that this path could path towards
     */
    private final Set<BlockPos> positions;

    private @Nullable Supplier<Path> task;
    private volatile @Nullable Path ret;

    /**
     * The block we're trying to path to
     * <p>
     * While processing, we have no idea where this is so consumers of `Path` should check that the path is processed before checking the target block
     */
    private BlockPos target;
    /**
     * How far we are to the target
     * <p>
     * While processing, the target could be anywhere, but theoretically we're always "close" to a theoretical target so default is 0
     */
    private float distToTarget = 0;
    /**
     * Whether we can reach the target
     * <p>
     * While processing, we can always theoretically reach the target so default is true
     */
    private boolean canReach = true;

    @SuppressWarnings("ConstantConditions")
    public AsyncPath(List<Node> emptyNodeList, Set<BlockPos> positions, Supplier<Path> pathSupplier) {
        super(emptyNodeList, null, false);

        this.positions = positions;
        this.task = pathSupplier;

        AsyncPathProcessor.queue(this);
    }

    @Override
    public void run() {
        Supplier<Path> task = this.task;
        if (task != null) {
            this.ret = task.get();
        }
    }

    @Override
    public boolean isProcessed() {
        return this.ready;
    }

    /**
     * Returns the future representing the processing state of this path
     */
    public final void schedulePostProcessing(Runnable runnable) {
        if (this.ready) {
            runnable.run();
        } else {
            this.postProcessing.offer(runnable);
        }
    }

    /**
     * An easy way to check if this processing path is the same as an attempted new path
     *
     * @param positions - the positions to compare against
     * @return true if we are processing the same positions
     */
    public final boolean hasSameProcessingPositions(final Set<BlockPos> positions) {
        return this.positions.equals(positions);
    }

    /**
     * Starts processing this path
     * Since this is no longer a synchronized function, checkProcessed is no longer required
     */
    private final void process() {
        if (this.ready) {
            return;
        }
        final Path ret = this.ret;
        final Path bestPath = ret != null ? ret : this.task.get();
        complete(bestPath);
    }

    // not this.ready
    private final void complete(Path bestPath) {
        this.nodes = bestPath.nodes;
        this.target = bestPath.getTarget();
        this.distToTarget = bestPath.getDistToTarget();
        this.canReach = bestPath.canReach();
        this.task = null;
        this.ready = true;

        this.runAllPostProcessing(TickThread.isTickThread());
    }

    private void runAllPostProcessing(boolean isTickThread) {
        Runnable runnable;
        while ((runnable = this.postProcessing.poll()) != null) {
            if (isTickThread) {
                runnable.run();
            } else {
                MinecraftServer.getServer().scheduleOnMain(runnable);
            }
        }
    }

    /*
     * Overrides we need for final fields that we cannot modify after processing
     */

    @Override
    public BlockPos getTarget() {
        this.process();
        return this.target;
    }

    @Override
    public float getDistToTarget() {
        this.process();
        return this.distToTarget;
    }

    @Override
    public boolean canReach() {
        this.process();
        return this.canReach;
    }

    /*
     * Overrides to ensure we're processed first
     */

    @Override
    public boolean isDone() {
        boolean ready = this.ready;
        if (!ready) {
            Path ret = this.ret;
            if (ret != null) {
                complete(ret);
            }
        }
        return ready && super.isDone();
    }

    @Override
    public void advance() {
        this.process();
        super.advance();
    }

    @Override
    public boolean notStarted() {
        this.process();
        return super.notStarted();
    }

    @Override
    public @Nullable Node getEndNode() {
        this.process();
        return super.getEndNode();
    }

    @Override
    public Node getNode(int index) {
        this.process();
        return super.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        this.process();
        super.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, Node node) {
        this.process();
        super.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        this.process();
        return super.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        this.process();
        return super.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int nodeIndex) {
        this.process();
        super.setNextNodeIndex(nodeIndex);
    }

    @Override
    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        this.process();
        return super.getEntityPosAtNode(entity, index);
    }

    @Override
    public BlockPos getNodePos(int index) {
        this.process();
        return super.getNodePos(index);
    }

    @Override
    public Vec3 getNextEntityPos(Entity entity) {
        this.process();
        return super.getNextEntityPos(entity);
    }

    @Override
    public BlockPos getNextNodePos() {
        this.process();
        return super.getNextNodePos();
    }

    @Override
    public Node getNextNode() {
        this.process();
        return super.getNextNode();
    }

    @Override
    public @Nullable Node getPreviousNode() {
        this.process();
        return super.getPreviousNode();
    }

}
