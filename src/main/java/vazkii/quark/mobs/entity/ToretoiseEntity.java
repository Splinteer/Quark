package vazkii.quark.mobs.entity;

import java.util.Random;

import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.BreedGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.RandomWalkingGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ToolType;
import vazkii.quark.base.handler.MiscUtil;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.mobs.module.ToretoiseModule;
import vazkii.quark.world.module.CaveRootsModule;

public class ToretoiseEntity extends AnimalEntity {

	public static final int ORE_TYPES = 4; 
	
	private static final String TAG_TAMED = "tamed";
	private static final String TAG_ORE = "oreType";
	
	public int rideTime;
	private boolean isTamed;
	
	private Ingredient goodFood;

	private static final DataParameter<Integer> ORE_TYPE = EntityDataManager.createKey(ToretoiseEntity.class, DataSerializers.VARINT);

	public ToretoiseEntity(EntityType<? extends ToretoiseEntity> type, World world) {
		super(type, world);
		stepHeight = 1.0F;
		setPathPriority(PathNodeType.WATER, 1.0F);
	}
	
	@Override
	protected void registerData() {
		super.registerData();
		
		dataManager.register(ORE_TYPE, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new SwimGoal(this));
		goalSelector.addGoal(1, new BreedGoal(this, 1.0));
		goalSelector.addGoal(2, new TemptGoal(this, 1.25, getGoodFood(), false));
		goalSelector.addGoal(3, new FollowParentGoal(this, 1.25));
		goalSelector.addGoal(5, new RandomWalkingGoal(this, 1.0D));
		goalSelector.addGoal(4, new LookAtGoal(this, PlayerEntity.class, 6.0F));
		goalSelector.addGoal(5, new LookRandomlyGoal(this));
	}
	
	private Ingredient getGoodFood() {
		if(goodFood == null)
			goodFood = Ingredient.fromItems(ModuleLoader.INSTANCE.isModuleEnabled(CaveRootsModule.class) ? CaveRootsModule.rootItem : Items.CACTUS);
		
		return goodFood;
	}
	
	@Override
	public boolean canBreed() {
		return getOreType() == 0;
	}
	
	@Override
	public SoundEvent getEatSound(ItemStack itemStackIn) {
		return null;
	}

	@Override
	public boolean isEntityInsideOpaqueBlock() {
		return MiscUtil.isEntityInsideOpaqueBlock(this);
	}

	@Override
	public void tick() {
		super.tick();

		Entity riding = getRidingEntity();
		if(riding != null)
			rideTime++;
		else rideTime = 0;
	}
	
	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		Entity e = source.getImmediateSource();
		int ore = getOreType();
		if(e instanceof LivingEntity && ore != 0) {
			LivingEntity living = (LivingEntity) e;
			ItemStack held = living.getHeldItemMainhand();
			
			if(held.getItem().getToolTypes(held).contains(ToolType.PICKAXE)) {
				if(!world.isRemote) {
					if(held.isDamageable() && e instanceof PlayerEntity)
						MiscUtil.damageStack((PlayerEntity) e, Hand.MAIN_HAND, held, 1);
					
					if(world instanceof ServerWorld)
						((ServerWorld) world).playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_LANTERN_BREAK, SoundCategory.NEUTRAL, 1F, 0.6F);
					
					Item drop = null;
					int countMult = 1;
					switch(ore) {
					case 1: 
						drop = Items.COAL;
						break;
					case 2:
						drop = Items.IRON_NUGGET;
						countMult *= 9;
						break;
					case 3:
						drop = Items.REDSTONE;
						countMult *= 3;
						break;
					case 4:
						drop = Items.LAPIS_LAZULI;
						countMult *= 2;
						break;
					}
					
					if(drop != null) {
						int count = 1;
						while(rand.nextBoolean())
							count++;
						count *= countMult;
						
						entityDropItem(new ItemStack(drop, count), 1.2F);
					}
					
					dataManager.set(ORE_TYPE, 0);
				}

				return false;
			}
		}
		
		return super.attackEntityFrom(source, amount);
	}
	
	@Override
	public void setInLove(PlayerEntity player) {
		setInLove(0);
	}

	@Override
	public void setInLove(int ticks) {
		if(world.isRemote)
			return;
		
        playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F + 0.5F * world.rand.nextInt(2), (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);
		heal(8);
		
		if(!isTamed) {
			isTamed = true;
			
			if(world instanceof ServerWorld)
				((ServerWorld) world).spawnParticle(ParticleTypes.HEART, getPosX(), getPosY(), getPosZ(), 20, 0.5, 0.5, 0.5, 0);
		} else {
			popOre();
		}
	}
	
	private void popOre() {
		if(getOreType() == 0 && world.rand.nextInt(3) == 0) {
			int ore = rand.nextInt(ORE_TYPES) + 1;
			dataManager.set(ORE_TYPE, ore);
			
			if(world instanceof ServerWorld) {
				((ServerWorld) world).spawnParticle(ParticleTypes.CLOUD, getPosX(), getPosY() + 0.5, getPosZ(), 100, 0.6, 0.6, 0.6, 0);
				((ServerWorld) world).playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.NEUTRAL, 10, 0.7F);
			}
		}
	}
	
	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return getGoodFood().test(stack);
	}
	
	@Override
	public boolean canDespawn(double distanceToClosestPlayer) {
		return !isTamed;
	}
	
	public static boolean canSpawnHere(IWorld world, BlockPos pos, Random rand) {
		if (world.getLightFor(LightType.SKY, pos) > rand.nextInt(32)) {
			return false;
		} else {
			int light = world.getWorld().isThundering() ? world.getNeighborAwareLightSubtracted(pos, 10) : world.getLight(pos);
			return light <= rand.nextInt(8);
		}
	}

	public static boolean spawnPredicate(EntityType<? extends ToretoiseEntity> type, IWorld world, SpawnReason reason, BlockPos pos, Random rand) {
		return canSpawnHere(world, pos, rand) && pos.getY() <= ToretoiseModule.maxYLevel;
	}
	
	@Override
	protected void jump() {
		// NO-OP
	}

	@Override
	public boolean onLivingFall(float distance, float damageMultiplier) {
		return false;
	}

	@Override
	protected float getWaterSlowDown() {
		return 0.9F;
	}

	@Override
	public boolean canBeLeashedTo(PlayerEntity player) {
		return false;
	}

	@Override
	protected float getSoundPitch() {
		return (rand.nextFloat() - rand.nextFloat()) * 0.2F + 0.6F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_TURTLE_AMBIENT_LAND;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.ENTITY_TURTLE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_TURTLE_DEATH;
	}
	
	public int getOreType() {
		return dataManager.get(ORE_TYPE);
	}
	
	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putBoolean(TAG_TAMED, isTamed);
		compound.putInt(TAG_ORE, getOreType());
	}
	
	@Override
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		isTamed = compound.getBoolean(TAG_TAMED);
		dataManager.set(ORE_TYPE, compound.getInt(TAG_ORE));
	}

	protected void registerAttributes() {
		super.registerAttributes();
		getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(60);
		getAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.08);
	}

	@Override
	public AgeableEntity createChild(AgeableEntity arg0) {
		ToretoiseEntity e = new ToretoiseEntity(ToretoiseModule.toretoiseType, world);
		e.remove(); // kill the entity cuz toretoise doesn't make babies
		return e;
	}

}