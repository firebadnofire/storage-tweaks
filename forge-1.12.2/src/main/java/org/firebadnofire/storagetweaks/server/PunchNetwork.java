package org.firebadnofire.storagetweaks.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.ILockableContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.ItemHandlerHelper;
import org.firebadnofire.storagetweaks.*;
import org.firebadnofire.storagetweaks.protocol.RequestGate;
import org.firebadnofire.storagetweaks.protocol.RequestBudget;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PunchNetwork {
    public static final int VERSION=1;
    public static SimpleNetworkWrapper channel;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,RequestBudget> ARRIVALS=new ConcurrentHashMap<>();
    private static final Map<UUID,RequestBudget> PROBE_ARRIVALS=new ConcurrentHashMap<>();
    private static class Session {
        long session=RANDOM.nextLong(), token, issued;
        long previousToken,previousIssued;
        boolean issuedToken,hasPrevious;
        boolean confirmed,clientPunch,clientOverride;
        int offers;
        RequestGate gate=new RequestGate(0);
        RequestGate probeGate=new RequestGate(0);
    }
    /** Fixed-size packets: no client-supplied item data or arbitrary inventory slot mutations. */
    public static class Message implements IMessage {
        public int type, version=VERSION, slot, count;
        public boolean enabled;
        public long session, token, request;
        public BlockPos pos=BlockPos.ORIGIN;
        public Message() { }
        @Override public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes()!=46) throw new IllegalArgumentException("Invalid Storage Tweaks packet length");
            type=buf.readUnsignedByte(); version=buf.readInt(); enabled=buf.readBoolean();
            session=buf.readLong(); token=buf.readLong(); request=buf.readLong();
            pos=BlockPos.fromLong(buf.readLong()); slot=buf.readInt(); count=buf.readInt();
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeByte(type).writeInt(version).writeBoolean(enabled).writeLong(session).writeLong(token)
                    .writeLong(request).writeLong(pos.toLong()).writeInt(slot).writeInt(count);
        }
    }
    public static class ClientHandler implements IMessageHandler<Message,IMessage> {
        @Override public IMessage onMessage(Message message, MessageContext context) { StorageTweaks.proxy.capabilities(message); return null; }
    }
    public static class ServerHandler implements IMessageHandler<Message,IMessage> {
        @Override public IMessage onMessage(Message message, MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            RequestBudget budget=(message.type==5?PROBE_ARRIVALS:ARRIVALS).computeIfAbsent(player.getUniqueID(),id -> new RequestBudget());
            if(!budget.allow(System.nanoTime())) return null;
            player.getServerWorld().addScheduledTask(() -> receive(player,message)); return null;
        }
    }
    public static void init() {
        channel=NetworkRegistry.INSTANCE.newSimpleChannel("fbstorage");
        channel.registerMessage(ClientHandler.class,Message.class,0,Side.CLIENT);
        channel.registerMessage(ServerHandler.class,Message.class,1,Side.SERVER);
        MinecraftForge.EVENT_BUS.register(new PunchNetwork());
    }
    @SubscribeEvent public void protectDepositAttack(PlayerInteractEvent.LeftClickBlock event) {
        if(event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player=(EntityPlayerMP)event.getEntityPlayer();
        Session session=SESSIONS.get(player.getUniqueID());
        if(session==null || !session.confirmed || !session.clientPunch || !Settings.serverPunch || !PunchAccess.blocker().isEmpty() ||
                player.openContainer!=player.inventoryContainer || !eligible(player.getHeldItemMainhand(),session.clientOverride) ||
                !player.world.isBlockLoaded(event.getPos())) return;
        Block block=player.world.getBlockState(event.getPos()).getBlock();
        if(block.getClass()!=BlockChest.class && block.getClass()!=BlockEnderChest.class) return;
        // 1.12's creative controller sends START_DESTROY before its client event.
        // Suppress only that vanilla mutation; deposits still require a fresh,
        // independently validated protocol request. Unmodded sessions are untouched.
        event.setCanceled(true);
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketBlockChange(player.world,event.getPos()));
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player=(EntityPlayerMP)event.player;
        NetworkDispatcher dispatcher=NetworkDispatcher.get(player.connection.netManager);
        if (!player.connection.netManager.isLocalChannel() && (dispatcher==null || !dispatcher.getModList().containsKey(StorageTweaks.ID))) return;
        Session session=new Session(); SESSIONS.put(player.getUniqueID(),session); offer(player,session,0);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.player.getUniqueID()); ARRIVALS.remove(event.player.getUniqueID());
        PROBE_ARRIVALS.remove(event.player.getUniqueID());
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { renew(event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { renew(event.player); }
    private static void renew(EntityPlayer player) {
        if(player instanceof EntityPlayerMP && SESSIONS.containsKey(player.getUniqueID())) {
            Session replacement=new Session();SESSIONS.put(player.getUniqueID(),replacement);
            ARRIVALS.remove(player.getUniqueID());PROBE_ARRIVALS.remove(player.getUniqueID());
            offer((EntityPlayerMP)player,replacement,0);
        }
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || FMLCommonHandler.instance().getMinecraftServerInstance()==null) return;
        for (EntityPlayerMP player:FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers()) {
            Session session=SESSIONS.get(player.getUniqueID());
            if (session!=null && (session.confirmed || session.offers<3) && player.getServerWorld().getTotalWorldTime()-session.issued>=100)
                offer(player,session,session.confirmed?2:0);
        }
    }
    private static void offer(EntityPlayerMP player,Session session,int type) {
        if(!session.confirmed) session.offers++;
        if(session.issuedToken) { session.previousToken=session.token;session.previousIssued=session.issued;session.hasPrevious=true; }
        session.issued=player.getServerWorld().getTotalWorldTime(); session.token=RANDOM.nextLong();
        session.issuedToken=true;
        Message message=new Message(); message.type=type; message.session=session.session; message.token=session.token;
        message.enabled=Settings.serverPunch && PunchAccess.blocker().isEmpty(); channel.sendTo(message,player);
    }
    public static void refreshCapabilities(net.minecraft.server.MinecraftServer server) {
        for(EntityPlayerMP player:server.getPlayerList().getPlayers()) {
            Session session=SESSIONS.get(player.getUniqueID());
            if(session!=null && session.confirmed) offer(player,session,2);
        }
    }
    private static void receive(EntityPlayerMP player,Message message) {
        Session session=SESSIONS.get(player.getUniqueID());
        if(Boolean.getBoolean("storageTweaks.runtimeTest") && message.type==3)
            StorageTweaks.LOG.info("PUNCH_TRACE request={} session={} token={} confirmed={} slot={}/{} held={} time={} issued={}",message.request,
                    session!=null && message.session==session.session,session!=null && message.token==session.token,session!=null && session.confirmed,
                    message.slot,player.inventory.currentItem,player.getHeldItemMainhand(),player.getServerWorld().getTotalWorldTime(),session==null?-1:session.issued);
        if (session==null || message.version!=VERSION || message.session!=session.session) return;
        long now=player.getServerWorld().getTotalWorldTime();
        long tokenIssued;
        if(message.token==session.token) tokenIssued=session.issued;
        else if(session.hasPrevious && message.token==session.previousToken) tokenIssued=session.previousIssued;
        else return;
        if(now<tokenIssued || now-tokenIssued>200) return;
        if (message.type==1) { session.confirmed=true;session.clientPunch=message.enabled;session.clientOverride=message.slot==1;offer(player,session,2);return; }
        if(message.type==7 && session.confirmed) { session.clientPunch=message.enabled;session.clientOverride=message.slot==1;return; }
        if ((message.type!=3 && message.type!=5) || !session.confirmed || !Settings.serverPunch) return;
        boolean probe=message.type==5;
        boolean accepted=(probe?session.probeGate:session.gate).consume(message.request,now,tokenIssued,message.slot,player.inventory.currentItem);
        if(!accepted && !probe) return;
        // A valid session's rejected probe still needs a response so slot-update
        // races and throttling cannot strand client availability until timeout.
        int deposited=accepted?deposit(player,message.pos,!probe,message.enabled):0;
        if(Boolean.getBoolean("storageTweaks.runtimeTest") && !probe) StorageTweaks.LOG.info("PUNCH_TRACE deposited={}",deposited);
        Message response=new Message(); response.type=probe?6:4; response.session=session.session; response.token=message.token;
        response.request=message.request; response.pos=message.pos;response.slot=message.slot;
        response.enabled=deposited>0;response.count=deposited; channel.sendTo(response,player);
    }
    public static boolean requiresOverride(ItemStack stack) {
        if(stack.isEmpty()) return false;
        Item item=stack.getItem();
        return item instanceof ItemTool || item instanceof ItemHoe || item instanceof ItemSword || item instanceof ItemBow ||
                item instanceof ItemShears || stack.isItemStackDamageable() || !item.getToolClasses(stack).isEmpty() ||
                Arrays.asList(Settings.punchExclusions).contains(String.valueOf(item.getRegistryName()));
    }
    public static boolean eligible(ItemStack stack,boolean override) {
        return !stack.isEmpty() && (override || !requiresOverride(stack));
    }
    private static int deposit(EntityPlayerMP player,BlockPos pos,boolean mutate,boolean override) {
        if(Boolean.getBoolean("storageTweaks.runtimeTest") && mutate)
            StorageTweaks.LOG.info("PUNCH_TRACE alive={} spectator={} container={} cursor={} eligible={} loaded={}",player.isEntityAlive(),player.isSpectator(),
                    player.openContainer==player.inventoryContainer,player.inventory.getItemStack(),eligible(player.getHeldItemMainhand(),override),player.world.isBlockLoaded(pos));
        if (!player.isEntityAlive() || player.isSpectator() || player.openContainer!=player.inventoryContainer ||
                !player.inventory.getItemStack().isEmpty() || !eligible(player.getHeldItemMainhand(),override) ||
                !player.world.isBlockLoaded(pos) || player.getServer()==null) return 0;
        double reach=player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d eyes=player.getPositionEyes(1); RayTraceResult hit=player.world.rayTraceBlocks(eyes,eyes.add(player.getLookVec().scale(reach)),false,true,false);
        if(Boolean.getBoolean("storageTweaks.runtimeTest") && mutate)
            StorageTweaks.LOG.info("PUNCH_TRACE target={} hit={} yaw={} pitch={} pos={}",pos,hit,player.rotationYaw,player.rotationPitch,player.getPositionVector());
        if (hit==null || hit.typeOfHit!=RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return 0;
        if (player.getServer().isBlockProtected(player.world,pos,player) || !player.world.isBlockModifiable(player,pos) || !PunchAccess.allowed(player,pos)) return 0;
        Block block=player.world.getBlockState(pos).getBlock(); IInventory inventory;
        TileEntity original=player.world.getTileEntity(pos);
        Map<BlockPos,TileEntity> accessed=new HashMap<>(); accessed.put(pos,original);
        if (block.getClass()==BlockChest.class) {
            // A double chest exposes BOTH halves. Never load a neighbor chunk or authorize just one half.
            for(EnumFacing face:EnumFacing.HORIZONTALS) {
                BlockPos adjacent=pos.offset(face);
                if(!player.world.isBlockLoaded(adjacent)) return 0;
                if(player.world.getBlockState(adjacent).getBlock()!=block) continue;
                if(player.getServer().isBlockProtected(player.world,adjacent,player) || !player.world.isBlockModifiable(player,adjacent) ||
                        !PunchAccess.allowed(player,adjacent)) return 0;
                PlayerInteractEvent.RightClickBlock neighborEvent=new PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,adjacent,hit.sideHit,hit.hitVec);
                if(MinecraftForge.EVENT_BUS.post(neighborEvent) || neighborEvent.getUseBlock()==Event.Result.DENY) return 0;
                accessed.put(adjacent,player.world.getTileEntity(adjacent));
            }
            ILockableContainer chest=((BlockChest)block).getLockableContainer(player.world,pos);
            if (chest==null || !chest.isUsableByPlayer(player) || !player.canOpen(chest.getLockCode())) return 0;
            inventory=chest;
        } else if (block.getClass()==BlockEnderChest.class) {
            if (player.world.getBlockState(pos.up()).doesSideBlockChestOpening(player.world,pos.up(),EnumFacing.DOWN)) return 0;
            inventory=player.getInventoryEnderChest();
        } else return 0;
        PlayerInteractEvent.RightClickBlock event=new PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,pos,hit.sideHit,hit.hitVec);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getUseBlock()==Event.Result.DENY) return 0;
        if (!PunchAccess.allowed(player,pos) || player.world.getTileEntity(pos)!=original || !eligible(player.getHeldItemMainhand(),override)) return 0;
        for(Map.Entry<BlockPos,TileEntity> target:accessed.entrySet()) {
            if(player.world.getTileEntity(target.getKey())!=target.getValue() || !PunchAccess.allowed(player,target.getKey())) return 0;
        }
        if(inventory instanceof ILockableContainer && !player.canOpen(((ILockableContainer)inventory).getLockCode())) return 0;
        if(block.getClass()==BlockChest.class && ((BlockChest)block).getLockableContainer(player.world,pos)==null) return 0;
        // Only vanilla inventories are admitted above. Plan the complete insertion before mutating either side.
        ItemStack held=player.getHeldItemMainhand(); int remaining=held.getCount();
        List<ItemStack> output=new ArrayList<>();
        for(int i=0;i<inventory.getSizeInventory();i++) output.add(inventory.getStackInSlot(i).copy());
        for(int pass=0;pass<2;pass++) for(int i=0;i<output.size() && remaining>0;i++) {
            ItemStack target=output.get(i);
            if (!inventory.isItemValidForSlot(i,held) || (pass==0 ? target.isEmpty() || !ItemHandlerHelper.canItemStacksStack(target,held) : !target.isEmpty())) continue;
            int moved=Math.min(remaining,Math.min(inventory.getInventoryStackLimit(),held.getMaxStackSize())-target.getCount());
            if (moved<=0) continue;
            ItemStack replacement=held.copy(); replacement.setCount(target.getCount()+moved); output.set(i,replacement); remaining-=moved;
        }
        int moved=held.getCount()-remaining;
        if (moved>0 && mutate) {
            for(int i=0;i<output.size();i++) inventory.setInventorySlotContents(i,output.get(i));
            held.shrink(moved); inventory.markDirty(); player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
        }
        return moved;
    }
}
