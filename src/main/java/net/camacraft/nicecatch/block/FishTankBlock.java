package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishConversion;
import net.camacraft.nicecatch.server.FishProfiles;
import net.camacraft.nicecatch.server.FishSizing;
import net.camacraft.nicecatch.server.FishTraits;
import net.camacraft.nicecatch.server.NiceCatchCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The fish tank, built like the real thing: glass walls around REAL water holding a REAL
 * fish. A filled tank block genuinely contains a water source (fish AI, buoyancy, the
 * pitch animation, biome-tinted vanilla water rendering — all of it just works), its
 * collision is only the outer glass walls (open toward adjoining tanks, so a bank of
 * tanks is one swimmable pool), and the occupant is a genuine entity swimming its genuine
 * AI inside the glass.
 *
 * Buckets fill the structure from the BOTTOM UP, one block of water per bucket. Only LIVE
 * fish go in: a caught fish whose item still carries its entity snapshot, a fish bucket,
 * or a fish spawn egg. Right-click with an empty hand to scoop the nearest fish back out
 * as its stamped item.
 */
public class FishTankBlock extends Block
{
    public static final Map<Direction, BooleanProperty> CONNECTED = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN);

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    /** How many joined tank blocks one structure scan follows (a 4x4x4 build). */
    private static final int REGION_CAP = 64;

    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    public FishTankBlock(Properties properties)
    {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FILLED, false);
        for (var property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        CONNECTED.values().forEach(builder::add);
        builder.add(FILLED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context)
    {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTED.get(dir),
                    context.getLevel().getBlockState(context.getClickedPos().relative(dir)).is(this));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        return state.setValue(CONNECTED.get(direction), neighbor.is(this));
    }

    // ---- The water is real ----

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(FILLED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    // ---- Glass optics: no shadows, no light blocking ----

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction)
    {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    // ---- Only the walls collide: the inside is open water ----

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE_CACHE.computeIfAbsent(state, s -> {
            VoxelShape shape = Shapes.empty();
            if (!s.getValue(CONNECTED.get(Direction.DOWN)))
                shape = Shapes.or(shape, Block.box(0, 0, 0, 16, 1, 16));
            if (!s.getValue(CONNECTED.get(Direction.UP)))
                shape = Shapes.or(shape, Block.box(0, 15, 0, 16, 16, 16));
            if (!s.getValue(CONNECTED.get(Direction.NORTH)))
                shape = Shapes.or(shape, Block.box(0, 0, 0, 16, 16, 1));
            if (!s.getValue(CONNECTED.get(Direction.SOUTH)))
                shape = Shapes.or(shape, Block.box(0, 0, 15, 16, 16, 16));
            if (!s.getValue(CONNECTED.get(Direction.WEST)))
                shape = Shapes.or(shape, Block.box(0, 0, 0, 1, 16, 16));
            if (!s.getValue(CONNECTED.get(Direction.EAST)))
                shape = Shapes.or(shape, Block.box(15, 0, 0, 16, 16, 16));
            return shape.isEmpty() ? Shapes.empty() : shape.optimize();
        });
    }

    // ---- Using the tank ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit)
    {
        ItemStack held = player.getItemInHand(hand);

        // A water bucket fills the STRUCTURE from the bottom up, one block per bucket.
        if (held.is(Items.WATER_BUCKET)) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.sidedSuccess(true);
            }
            BlockPos target = lowestUnfilled(serverLevel, pos);
            if (target == null) return InteractionResult.PASS; // brim-full already
            serverLevel.setBlock(target, serverLevel.getBlockState(target).setValue(FILLED, true), 3);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            serverLevel.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.sidedSuccess(false);
        }

        // Live fish go in: snapshot items, buckets, spawn eggs.
        EntityType<?> insertType = tankableType(held, level);
        if (insertType != null) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.sidedSuccess(true);
            }
            return insertFish(serverLevel, pos, player, hand, held, insertType)
                    ? InteractionResult.sidedSuccess(false) : InteractionResult.CONSUME;
        }

        // An empty hand scoops the nearest fish back out, snapshot and all.
        if (held.isEmpty()) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.sidedSuccess(true);
            }
            PathfinderMob fish = nearestFish(serverLevel, pos);
            if (fish == null) return InteractionResult.PASS;
            ItemStack scooped = FishConversion.snapshotItem(serverLevel, fish);
            fish.discard();
            if (!player.getInventory().add(scooped)) {
                player.drop(scooped, false);
            }
            serverLevel.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.6F, 1.0F);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /**
     * What kind of live fish (if any) this stack can put in a tank. A caught fish item
     * qualifies only while it carries its entity snapshot — a plain fillet is dead and
     * stays dinner. Fish buckets and fish spawn eggs are alive by definition.
     */
    @Nullable
    public static EntityType<?> tankableType(ItemStack stack, Level level)
    {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof SpawnEggItem egg) {
            EntityType<?> type = egg.getType(stack.getTag());
            return NiceCatchCommands.isFishType(type, level) ? type : null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        if (id.getPath().endsWith("_bucket")) {
            ResourceLocation entityId = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 7));
            if (ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
                return NiceCatchCommands.isFishType(type, level) ? type : null;
            }
            return null;
        }
        if (FishConversion.hasLiveSnapshot(stack) && ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            return NiceCatchCommands.isFishType(type, level) ? type : null;
        }
        return null;
    }

    private boolean insertFish(ServerLevel level, BlockPos pos, Player player, InteractionHand hand,
                               ItemStack held, EntityType<?> type)
    {
        List<BlockPos> cells = connectedCells(level, pos, true);
        if (cells.isEmpty()) {
            player.displayClientMessage(Component.translatable("nicecatch.tank.needs_water"), true);
            return false;
        }

        Entity created = type.create(level);
        if (!(created instanceof PathfinderMob fish)) {
            if (created != null) created.discard();
            return false;
        }

        CompoundTag info = held.getTagElement("NiceCatch");
        if (info != null && info.contains("Entity")) {
            fish.load(info.getCompound("Entity").copy()); // the exact caught individual
        } else if (fish instanceof Bucketable bucketable && held.getTag() != null
                && !(held.getItem() instanceof SpawnEggItem)) {
            bucketable.loadFromBucketTag(held.getTag()); // bucket variants (tropical patterns)
        } else {
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null, null);
        }

        // The tank must actually FIT the fish — a trophy tuna needs a trophy aquarium.
        AABB bounds = cellBounds(cells);
        float scale = FishSizing.scaleFor(type, fish.getUUID(), FishTraits.of(fish));
        float width = type.getWidth() * scale;
        float height = type.getHeight() * scale;
        if (width > Math.min(bounds.getXsize(), bounds.getZsize()) - 0.25D
                || height > bounds.getYsize() - 0.25D) {
            fish.discard();
            player.displayClientMessage(Component.translatable("nicecatch.tank.too_small"), true);
            return false;
        }

        BlockPos home = nearestCell(cells, pos);
        fish.moveTo(home.getX() + 0.5D, home.getY() + 0.3D, home.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        fish.setPersistenceRequired();
        level.addFreshEntity(fish);
        if (!player.getAbilities().instabuild) {
            if (held.is(Items.COD_BUCKET) || held.getItem() instanceof net.minecraft.world.item.MobBucketItem) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            } else {
                held.shrink(1);
            }
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.6F, 1.1F);
        return true;
    }

    @Nullable
    private PathfinderMob nearestFish(ServerLevel level, BlockPos pos)
    {
        List<BlockPos> cells = connectedCells(level, pos, true);
        if (cells.isEmpty()) return null;
        AABB bounds = cellBounds(cells);
        PathfinderMob best = null;
        double bestDist = Double.MAX_VALUE;
        for (PathfinderMob fish : level.getEntitiesOfClass(PathfinderMob.class, bounds,
                f -> f.isAlive() && FishBehavior.isFishKind(f))) {
            double d = fish.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (d < bestDist) {
                bestDist = d;
                best = fish;
            }
        }
        return best;
    }

    /** All tank blocks joined to this one (optionally only water-filled ones). */
    private List<BlockPos> connectedCells(Level level, BlockPos start, boolean filledOnly)
    {
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var out = new ArrayList<BlockPos>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() <= REGION_CAP) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            if (!state.is(this)) continue;
            if (!filledOnly || state.getValue(FILLED)) {
                out.add(current);
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.add(next) && level.getBlockState(next).is(this)) {
                    queue.add(next);
                }
            }
        }
        return out;
    }

    /** Water settles: the next bucket goes to the LOWEST dry cell of the whole structure. */
    @Nullable
    private BlockPos lowestUnfilled(Level level, BlockPos start)
    {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos cell : connectedCells(level, start, false)) {
            if (level.getBlockState(cell).getValue(FILLED)) continue;
            double dist = cell.distSqr(start);
            if (best == null || cell.getY() < best.getY()
                    || (cell.getY() == best.getY() && dist < bestDist)) {
                best = cell;
                bestDist = dist;
            }
        }
        return best;
    }

    private static AABB cellBounds(List<BlockPos> cells)
    {
        BlockPos min = cells.get(0);
        BlockPos max = cells.get(0);
        for (BlockPos cell : cells) {
            min = new BlockPos(Math.min(min.getX(), cell.getX()),
                    Math.min(min.getY(), cell.getY()), Math.min(min.getZ(), cell.getZ()));
            max = new BlockPos(Math.max(max.getX(), cell.getX()),
                    Math.max(max.getY(), cell.getY()), Math.max(max.getZ(), cell.getZ()));
        }
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    private static BlockPos nearestCell(List<BlockPos> cells, BlockPos to)
    {
        BlockPos best = cells.get(0);
        double bestDist = Double.MAX_VALUE;
        for (BlockPos cell : cells) {
            double d = cell.distSqr(to);
            if (d < bestDist) {
                bestDist = d;
                best = cell;
            }
        }
        return best;
    }
}
