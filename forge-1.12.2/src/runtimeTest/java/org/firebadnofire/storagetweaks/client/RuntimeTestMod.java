package org.firebadnofire.storagetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.firebadnofire.storagetweaks.*;
import org.firebadnofire.storagetweaks.inventory.*;
import org.firebadnofire.storagetweaks.server.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.channel.*;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import net.minecraft.network.play.client.CPacketClickWindow;

/** Test mod, never included in production jars. Exercises actual integrated-server packets. */
@Mod(modid="storage_tweaks_runtime_tests",name="Storage Tweaks Runtime Tests",version="1",acceptableRemoteVersions="*")
public final class RuntimeTestMod {
    private final Minecraft mc=Minecraft.getMinecraft();
    private static final BlockPos CHEST=new BlockPos(0,4,3);
    private int stage, next, started, ticks;
    private boolean launched, failed;
    private Future<?> pending;
    private Snapshot expected;
    private List<org.firebadnofire.storagetweaks.inventory.Stack> hotbar;
    private GuiContainer matched;
    private GuiContainer suppressed;
    private GuiContainer buttonScreen;
    private float scanYaw,scanPitch;
    private boolean earlyHotbarSwitch;
    private int chestRowCase,chestSwapRepeat;
    private boolean chestRowsTested;
    private Snapshot chestRowInitial,chestRowExpected;
    private int overrideCase;
    private net.minecraft.client.settings.KeyBinding overrideBinding;
    private org.firebadnofire.storagetweaks.inventory.Stack overrideOriginal;
    private int rapidIndex;
    private final AtomicInteger delayedProbeReplies=new AtomicInteger();
    private int rowVariant;
    private boolean rowsTested;
    private List<ItemStack> rowExpected;
    private List<net.minecraft.client.gui.GuiButton> buttons;
    private volatile boolean allow=true;
    private volatile BlockPos deniedPosition;
    private int restrictionCase,restrictionPhase;
    private long request;
    private final AtomicInteger observedClicks=new AtomicInteger();
    private int savedTimeout;
    private int variant;
    private long previousSession;
    private final String mode=System.getProperty("storageTweaks.runtimeMode","integrated");

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(!Boolean.getBoolean("storageTweaks.runtimeTest")) return;
        mc.gameSettings.pauseOnLostFocus=false;
        mc.gameSettings.difficulty=EnumDifficulty.PEACEFUL;
        mc.gameSettings.setSoundLevel(net.minecraft.util.SoundCategory.MASTER,0F);
        PunchAccess.register("storage_tweaks_runtime_tests",(player,pos)->allow && !pos.equals(deniedPosition));
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
    public void hotbarInputTiming(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END && earlyHotbarSwitch) { mc.player.inventory.currentItem=1;earlyHotbarSwitch=false; }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || failed) return;
        ticks++;
        try {
            if(!launched && ticks>1200) throw new AssertionError("Startup screen blocked automatic launch: "+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getName()));
            if(mode.equals("vanilla")) { vanilla();return; }
            if(mode.equals("contention")) { contention();return; }
            if(mode.equals("lan-host") || mode.equals("lan-creative")) { lanHost();return; }
            if(!launched) {
                if(mc.currentScreen instanceof GuiMainMenu && ticks>40) {
                    launched=true; started=ticks;
                    WorldSettings settings=new WorldSettings(713,GameType.SURVIVAL,false,false,WorldType.FLAT);
                    mc.launchIntegratedServer("storage-tweaks-runtime-"+System.currentTimeMillis(),"Storage Tweaks runtime tests",settings);
                }
                return;
            }
            if(ticks-started>5000) throw new AssertionError("Runtime harness timed out at stage "+stage);
            if(mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected) throw new AssertionError("Disconnected during runtime test; inspect the preceding network exception");
            if(mc.player==null || mc.world==null || mc.getIntegratedServer()==null || ticks<next) return;
            if(pending!=null) { if(!pending.isDone()) return; pending.get();pending=null; next=ticks+15;return; }
            if(hotbar!=null && ClientProxy.JOB.active()) {
                Snapshot snapshot=InventoryAdapter.snapshot(mc.player.openContainer);
                int[] ids=InventoryAdapter.group(mc.player.openContainer,true,true);
                for(int slot:ids) if(mc.player.openContainer.getSlot(slot).getSlotIndex()<9)
                    require(snapshot.slots.get(slot).equals(hotbar.get(mc.player.openContainer.getSlot(slot).getSlotIndex())),"Intermediate hotbar write");
            }
            if(mode.startsWith("chest-rows") && stage==0) stage=120;
            if(mode.startsWith("tool-override") && stage==0) stage=100;
            if(mode.startsWith("rapid-punch") && stage==0) stage=90;
            if(mode.equals("row-swap") && stage==0) stage=80;
            if(mode.equals("creative-regression") && stage==0) stage=50;
            if(mode.equals("item-best") && stage==0) stage=56;
            if(mode.startsWith("punch-recovery") && stage==0) stage=60;
            switch(stage) {
                case 0:
                    pending=server(() -> {
                        EntityPlayerMP player=player();player.setGameType(GameType.SURVIVAL);
                        player.getServerWorld().getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
                        player.getServerWorld().getGameRules().setOrCreateGameRule("doMobSpawning","false");
                        player.connection.setPlayerLocation(.5,4,.5,0,20);
                        player.getServerWorld().setBlockState(CHEST,Blocks.CHEST.getDefaultState());
                        player.getServerWorld().setBlockState(CHEST.add(2,0,0),Blocks.CHEST.getDefaultState());
                        TileEntityChest chest=chest();chest.clear();
                        chest.setInventorySlotContents(0,new ItemStack(Blocks.STONE,20));
                        chest.setInventorySlotContents(1,new ItemStack(Items.APPLE,8));
                        chest.setInventorySlotContents(2,new ItemStack(Items.DIAMOND_SWORD).setStackDisplayName("A Named Sword"));
                        chest.setInventorySlotContents(3,new ItemStack(Items.APPLE,20));
                        player.inventory.clear();player.inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,10));
                        player.inventoryContainer.detectAndSendChanges();
                    }); stage++;break;
                case 1:
                    // Software-rendered clients may still be applying spawn chunks/teleport.
                    if(mc.currentScreen!=null || mc.player.getDistanceSq(CHEST)>16 ||
                            mc.world.getBlockState(CHEST).getBlock()!=Blocks.CHEST) return;
                    mc.player.rotationYaw=0;mc.player.rotationPitch=20;
                    StorageTweaks.LOG.info("FIXTURE_OPEN client={} sneaking={}",mc.player.getPositionVector(),mc.player.isSneaking());
                    mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                    stage++;next=ticks+20;break;
                case 2:
                    if(!(mc.currentScreen instanceof GuiContainer) && ticks<next+400) return;
                    require(mc.currentScreen instanceof GuiContainer,"Chest did not open");
                    start(0);stage++;break;
                case 3:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Take result mismatch");
                    pending=server(() -> require(chest().isEmpty(),"Take did not empty server chest"));stage++;pass("Take, including hotbar merging");break;
                case 4: start(1);stage++;break;
                case 5:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Store result mismatch");
                    pending=server(() -> { require(player().inventory.isEmpty(),"Store did not include all main/hotbar items");require(total(chest(),Items.APPLE)==28,"Store conservation"); });
                    stage++;pass("Store, including tools and hotbar");break;
                case 6:start(2);stage++;break;
                case 7:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Sort result mismatch");
                    pending=server(() -> require(chest().getStackInSlot(0).hasDisplayName(),"Localized/custom-name server sort"));
                    stage++;pass("Chest sorting and transaction confirmations");break;
                case 8:
                    mc.player.closeScreen();
                    pending=server(() -> {
                        player().inventory.clear();
                        for(int i=0;i<9;i++) player().inventory.setInventorySlotContents(i,new ItemStack(Items.DIAMOND,i+1));
                        player().inventory.setInventorySlotContents(9,new ItemStack(Blocks.STONE,20));
                        player().inventory.setInventorySlotContents(10,new ItemStack(Items.APPLE,8));
                        player().inventory.setInventorySlotContents(11,new ItemStack(Items.APPLE,20));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 9:
                    mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));
                    hotbar=new ArrayList<>();for(int i=0;i<9;i++) hotbar.add(InventoryAdapter.value(mc.player.inventory.getStackInSlot(i)));
                    start(2);stage++;break;
                case 10:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Player sort mismatch");
                    hotbar=null;pending=server(() -> { for(int i=0;i<9;i++) require(player().inventory.getStackInSlot(i).getCount()==i+1,"Server hotbar mutated"); });
                    stage++;pass("Player sort with hotbar unchanged throughout");break;
                case 11:
                    mc.player.closeScreen();
                    require(ClientCommandHandler.instance.getCommands().containsKey("cfind"),"Finder absent without clientcommands");
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfind @s");
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfindblock minecraft:chest");
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:apple");
                    stage++;next=ticks+40;break;
                case 12:
                    require(mc.currentScreen instanceof GuiContainer,"Item search failed to show matching chest");
                    require(!ClientProxy.finder.interacting(),"Screen suppression leaked after match");
                    matched=(GuiContainer)mc.currentScreen;
                    require(matched==suppressed,"Finder replaced the suppressed screen instead of revealing it");
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"item-finder-match.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    require(mc.player.openContainer==matched.inventorySlots,"Matching GUI lost its real container");
                    mc.player.closeScreen();
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:bedrock --keep-searching");
                    stage++;next=ticks+3;pass("Item finder match handoff to original container");break;
                case 13:
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem cancel");
                    require(!ClientProxy.finder.interacting(),"Cancellation leaked screen suppression");
                    pending=server(() -> {
                        chest().clear();player().inventory.clear();player().inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
                        player().inventory.currentItem=0;player().inventoryContainer.detectAndSendChanges();
                    });stage++;pass("Finder cancellation cleanup");break;
                case 14:
                    // A reply already in flight at cancellation may open a normal, visible GUI.
                    // Close that real GUI before the independent punch phase; never bypass its server restriction.
                    if(mc.currentScreen!=null) { pass("Late cancellation reply remained visible; closing before punch phase");mc.player.closeScreen();next=ticks+10;return; }
                    require(mc.player.openContainer==mc.player.inventoryContainer,"Punch phase started with an open container");
                    if(mode.equals("pack")) {
                        require(!ClientProxy.capabilityStatus.equals("available"),"Unreviewed pack bypassed conservative protection boundary");
                        pass("Unreviewed pack integrations conservatively disable punch");stage=22;break;
                    }
                    if(!ClientProxy.capabilityStatus.equals("available")) { if(ticks-started>1200) throw new AssertionError("Punch handshake unavailable: "+ClientProxy.capabilityStatus); return; }
                    punch(++request,CHEST);stage++;next=ticks+15;break;
                case 15:
                    pending=server(() -> { require(total(chest(),Items.DIAMOND)==7,"Punch deposit missing");require(player().getHeldItemMainhand().isEmpty(),"Punch held stack not consumed");
                        for(int i=0;i<27;i++) chest().setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));
                        chest().setInventorySlotContents(0,new ItemStack(Items.DIAMOND,63));
                        player().inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));player().inventoryContainer.detectAndSendChanges();
                    });stage++;pass("Negotiated punch deposit without GUI");break;
                case 16:punch(++request,CHEST);stage++;next=ticks+15;break;
                case 17:
                    pending=server(() -> { require(chest().getStackInSlot(0).getCount()==64,"Partial deposit target count");require(player().getHeldItemMainhand().getCount()==6,"Partial remainder"); });
                    stage++;pass("Partial punch deposit");break;
                case 18:punch(request,CHEST);stage++;next=ticks+15;break;
                case 19:
                    pending=server(() -> { require(player().getHeldItemMainhand().getCount()==6,"Replay mutated held item");chest().clear();allow=false; });stage++;break;
                case 20:punch(++request,CHEST);stage++;next=ticks+15;break;
                case 21:
                    pending=server(() -> { require(player().getHeldItemMainhand().getCount()==6 && chest().isEmpty(),"Protection policy bypass");allow=true; });
                    stage++;pass("Replay rejection and protection policy denial");break;
                case 22:
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfindblock cancel");
                    require(mc.currentScreen==null,"Punch unexpectedly opened GUI");
                    pending=server(() -> {
                        chest().clear();player().inventory.clear();
                        chest().setInventorySlotContents(0,new ItemStack(Blocks.STONE,20));
                        chest().setInventorySlotContents(1,new ItemStack(Items.APPLE,8));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 23:
                    mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                    stage++;next=ticks+20;break;
                case 24:
                    require(mc.currentScreen instanceof GuiContainer,"Timeout fixture chest did not open");
                    savedTimeout=Settings.timeout;Settings.timeout=20;observedClicks.set(0);
                    mc.getConnection().getNetworkManager().channel().pipeline().addBefore("storage_tweaks_observer","storage_test_drop_ack",new ChannelDuplexHandler() {
                        @Override public void channelRead(ChannelHandlerContext context,Object message) throws Exception {
                            if(!(message instanceof SPacketConfirmTransaction)) super.channelRead(context,message);
                        }
                        @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise) throws Exception {
                            if(message instanceof CPacketClickWindow) {
                                CPacketClickWindow click=(CPacketClickWindow)message;
                                StorageTweaks.LOG.info("DROP_ACK_TRACE click={} slot={} action={}",observedClicks.incrementAndGet(),click.getSlotId(),click.getActionNumber());
                            }
                            super.write(context,message,promise);
                        }
                    });
                    start(2);stage++;next=ticks+35;break;
                case 25:
                    require(!ClientProxy.JOB.active(),"Missing acknowledgment did not time out");
                    require(observedClicks.get()==1,"Expected exactly one click without acknowledgment; observed "+observedClicks.get());
                    mc.getConnection().getNetworkManager().channel().pipeline().remove("storage_test_drop_ack");
                    Settings.timeout=savedTimeout;
                    require(!mc.player.inventory.getItemStack().isEmpty(),"Timeout discarded cursor contents");
                    pending=server(() -> {
                        int stone=total(chest(),net.minecraft.item.Item.getItemFromBlock(Blocks.STONE));
                        ItemStack cursor=player().inventory.getItemStack();
                        if(cursor.getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE)) stone+=cursor.getCount();
                        require(stone==20,"Timeout changed authoritative item count");
                    });stage++;pass("Missing acknowledgment stops after one click and preserves cursor");break;
                case 26:
                    mc.player.closeScreen();
                    pending=server(() -> {
                        chest().clear();player().inventory.clear();
                        chest().setInventorySlotContents(0,new ItemStack(Blocks.STONE,20));
                        chest().setInventorySlotContents(1,new ItemStack(Items.APPLE,8));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 27:
                    mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                    stage++;next=ticks+20;break;
                case 28:
                    observedClicks.set(0);
                    mc.getConnection().getNetworkManager().channel().pipeline().addBefore("storage_tweaks_observer","storage_test_reject",new ChannelDuplexHandler() {
                        @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise) throws Exception {
                            if(message instanceof CPacketClickWindow && observedClicks.incrementAndGet()==1) {
                                CPacketClickWindow click=(CPacketClickWindow)message;
                                message=new CPacketClickWindow(click.getWindowId(),click.getSlotId(),click.getUsedButton(),click.getClickType(),ItemStack.EMPTY,click.getActionNumber());
                            }
                            super.write(context,message,promise);
                        }
                    });
                    start(2);stage++;next=ticks+20;break;
                case 29:
                    require(!ClientProxy.JOB.active(),"Rejected click left job active");
                    require(observedClicks.get()==1,"Rejected click sent subsequent mutations");
                    require(!mc.player.inventory.getItemStack().isEmpty(),"Rejected click lost recoverable cursor");
                    mc.getConnection().getNetworkManager().channel().pipeline().remove("storage_test_reject");
                    pending=server(() -> {
                        ItemStack cursor=player().inventory.getItemStack();
                        require(total(chest(),Items.APPLE)+(cursor.getItem()==Items.APPLE?cursor.getCount():0)==8,"Rejected transaction item conservation");
                        require(total(chest(),net.minecraft.item.Item.getItemFromBlock(Blocks.STONE))==20,"Rejected transaction stone conservation");
                    });stage++;pass("Explicit server rejection stops subsequent clicks and preserves items");break;
                case 30:
                    mc.player.closeScreen();
                    pending=server(() -> {
                        if(player().world.getTileEntity(CHEST) instanceof net.minecraft.inventory.IInventory)
                            ((net.minecraft.inventory.IInventory)player().world.getTileEntity(CHEST)).clear();
                        player().getServerWorld().setBlockState(CHEST,Blocks.AIR.getDefaultState());
                        player().getServerWorld().setBlockState(CHEST.east(),Blocks.AIR.getDefaultState());
                        net.minecraft.block.Block block=variant==1?Blocks.TRAPPED_CHEST:variant==2?Blocks.ENDER_CHEST:variant==3?Blocks.WHITE_SHULKER_BOX:Blocks.CHEST;
                        player().getServerWorld().setBlockState(CHEST,block.getDefaultState());
                        if(variant==0) player().getServerWorld().setBlockState(CHEST.east(),Blocks.CHEST.getDefaultState());
                        net.minecraft.inventory.IInventory inventory=variantStorage();inventory.clear();
                        inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,20));
                        inventory.setInventorySlotContents(1,new ItemStack(Items.APPLE,28));
                        player().inventory.clear();player().inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,10));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 31:
                    mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                    stage++;next=ticks+20;break;
                case 32:
                    require(mc.currentScreen instanceof GuiContainer,"Variant "+variant+" did not open");start(0);stage++;break;
                case 33:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Variant Take mismatch "+variant);
                    start(1);stage++;break;
                case 34:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Variant Store mismatch "+variant);
                    start(2);stage++;break;
                case 35:
                    if(ClientProxy.JOB.active()) return;
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Variant Sort mismatch "+variant);
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"storage-variant-"+variant+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    pending=server(() -> {
                        net.minecraft.inventory.IInventory inventory=variantStorage();
                        require(inventory.getStackInSlot(0).getItem()==Items.APPLE && inventory.getStackInSlot(0).getCount()==28,"Variant authoritative sort "+variant);
                        require(inventory.getStackInSlot(1).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE) && inventory.getStackInSlot(1).getCount()==30,"Variant authoritative conservation "+variant);
                    });
                    pass("Take/Store/Sort variant "+variant+" (double/trapped/ender/shulker)");stage++;break;
                case 36:
                    mc.player.closeScreen();variant++;stage=variant<4?30:37;break;
                case 37:
                    pending=server(() -> {
                        player().connection.setPlayerLocation(.5,4,.5,0,20);
                        variantStorage().clear();
                        player().getServerWorld().setBlockState(CHEST,Blocks.CHEST.getDefaultState());
                        net.minecraft.nbt.NBTTagCompound nested=new ItemStack(Items.DIAMOND,3).serializeNBT();nested.setByte("Slot",(byte)0);
                        net.minecraft.nbt.NBTTagList items=new net.minecraft.nbt.NBTTagList();items.appendTag(nested);
                        net.minecraft.nbt.NBTTagCompound block=new net.minecraft.nbt.NBTTagCompound();block.setTag("Items",items);
                        ItemStack box=new ItemStack(Blocks.WHITE_SHULKER_BOX);box.setTagInfo("BlockEntityTag",block);
                        chest().setInventorySlotContents(0,box);player().inventory.clear();player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 38:
                    if(mc.currentScreen!=null || mc.player.getDistanceSq(CHEST)>16 ||
                            mc.world.getBlockState(CHEST).getBlock()!=Blocks.CHEST) return;
                    boolean rejected=false;
                    try { ClientCommandHandler.instance.getCommands().get("cfind").execute(null,mc.player,new String[]{"@e[nbt=x]"}); }
                    catch(net.minecraft.command.CommandException expectedFailure) { rejected=true; }
                    require(rejected,"Unsupported selector filter was silently accepted");
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfind @a[type=player]");
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:diamond");stage++;next=ticks+40;break;
                case 39:
                    require(mc.currentScreen instanceof GuiContainer,"Nested shulker search failed to show matching chest");
                    require(mc.player.openContainer==((GuiContainer)mc.currentScreen).inventorySlots && !ClientProxy.finder.interacting(),"Nested search suppression leaked");
                    require(mc.player.openContainer.getSlot(0).getStack().getItem() instanceof net.minecraft.item.ItemShulkerBox,"Nested search matched wrong fixture");
                    mc.player.closeScreen();pass("Nested shulker item search and unsupported-selector rejection");stage=mode.equals("pack")?40:43;break;
                case 40:
                    if(mode.equals("pack")) { stage=42;break; }
                    Field sessionField=ClientProxy.class.getDeclaredField("session");sessionField.setAccessible(true);
                    previousSession=sessionField.getLong(StorageTweaks.proxy);
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:bedrock --keep-searching");
                    pending=server(() -> {
                        player().setGameType(GameType.CREATIVE);
                        // Direct test transfers have no last portal vector; use Forge's supported teleporter API.
                        player().changeDimension(-1,(world,entity,yaw) -> {
                            world.setBlockState(new BlockPos(0,79,0),Blocks.STONE.getDefaultState());
                            for(int height=80;height<84;height++) world.setBlockState(new BlockPos(0,height,0),Blocks.AIR.getDefaultState());
                            entity.setLocationAndAngles(.5,80,.5,0,0);
                        });
                    });stage++;next=ticks+40;break;
                case 41:
                    Field renewedField=ClientProxy.class.getDeclaredField("session");renewedField.setAccessible(true);
                    if(mc.player.dimension!=-1 || !ClientProxy.capabilityStatus.equals("available") || renewedField.getLong(StorageTweaks.proxy)==previousSession) return;
                    require(!ClientProxy.finder.interacting(),"Dimension change leaked finder suppression");
                    pass("Actual dimension transfer renews punch session and releases finder suppression");stage++;break;
                case 42:
                    if(mode.equals("pack") && rowsTested && !chestRowsTested) {stage=120;break;}
                    if(!rowsTested) { stage=80;break; }
                    if(!mode.equals("pack")) { stage=50;break; }
                    pass(mode.equals("pack")?"ALL PACK RUNTIME TESTS PASSED":"ALL INTEGRATED RUNTIME TESTS PASSED");stage++;mc.shutdown();break;
                case 120:
                    if(mc.currentScreen!=null) mc.player.closeScreen();
                    hotbar=null;Settings.chestButtons=chestRowCase!=0;
                    pending=server(() -> {
                        player().setGameType(mode.contains("creative")?GameType.CREATIVE:GameType.SURVIVAL);player().connection.setPlayerLocation(.5,4,.5,0,20);
                        for(BlockPos pos:new BlockPos[]{CHEST,CHEST.east()}) {
                            if(player().world.getTileEntity(pos) instanceof net.minecraft.inventory.IInventory)
                                ((net.minecraft.inventory.IInventory)player().world.getTileEntity(pos)).clear();
                            player().world.setBlockState(pos,Blocks.AIR.getDefaultState());
                            player().world.setBlockState(pos.up(),Blocks.AIR.getDefaultState());
                        }
                        player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());
                        if(chestRowCase>=6) player().world.setBlockState(CHEST.east(),Blocks.CHEST.getDefaultState());
                        net.minecraft.inventory.IInventory storage=((net.minecraft.block.BlockChest)Blocks.CHEST).getLockableContainer(player().world,CHEST);
                        for(int i=0;i<storage.getSizeInventory();i++) {
                            ItemStack stack=i%7==0?ItemStack.EMPTY:new ItemStack(Items.APPLE,1+i%63);
                            if(!stack.isEmpty() && i%3==0) stack.setStackDisplayName("Chest "+i);
                            storage.setInventorySlotContents(i,stack);
                        }
                        player().inventory.clear();
                        for(int i=0;i<36;i++) {
                            ItemStack stack=i%8==0?ItemStack.EMPTY:new ItemStack(Items.APPLE,2+i);
                            if(!stack.isEmpty() && i%4==0) stack.setStackDisplayName("Player "+i);
                            player().inventory.setInventorySlotContents(i,stack);
                        }
                        player().inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND_SWORD).setStackDisplayName("Hotbar sword"));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage=121;next=ticks+20;break;
                case 121:
                    if(mc.currentScreen!=null || mc.player.getDistanceSq(CHEST)>16 || mc.world.getBlockState(CHEST).getBlock()!=Blocks.CHEST || !ClientProxy.observing) {
                        if(ticks-next>160) throw new AssertionError("Chest fixture not ready: "+mc.currentScreen+" pos="+mc.player.getPositionVector());return;
                    }
                    LookCommand.lookAt(new Vec3d(CHEST).add(.5,.5,.5));mc.playerController.updateController();
                    mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                    stage=122;next=ticks+15;break;
                case 122:
                    if(!(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest)) {
                        if(ticks-next>100) throw new AssertionError("Chest row screen missing: "+mc.currentScreen+" pos="+mc.player.getPositionVector());return;
                    }
                    GuiContainer chestRowScreen=(GuiContainer)mc.currentScreen;
                    int storageSize=chestRowCase<6?27:54, number=chestRowCase<6?chestRowCase+1:chestRowCase-6+1;
                    int first=number<=3?storageSize+(3-number)*9:storageSize-(number-3)*9;
                    int count=0;
                    for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.matches("[1-9]")) {
                        count++;int n=Integer.parseInt(button.displayString);
                        int rowFirst=n<=3?storageSize+(3-n)*9:storageSize-(n-3)*9;
                        require(button.y==chestRowScreen.getGuiTop()+chestRowScreen.inventorySlots.getSlot(rowFirst).yPos,"Chest row label misaligned");
                        require(button.x<chestRowScreen.getGuiLeft(),"Chest row label not on left");
                    }
                    require(count==3+storageSize/9,"Wrong chest row button count or a hotbar button was added");
                    if(chestRowCase==0) for(net.minecraft.client.gui.GuiButton button:buttons) require(!button.displayString.equals("Take"),"Independent chest toggle ignored");
                    Field chestBindings=ClientProxy.class.getDeclaredField("swapRows");chestBindings.setAccessible(true);
                    net.minecraft.client.settings.KeyBinding[] rowBindings=(net.minecraft.client.settings.KeyBinding[])chestBindings.get(StorageTweaks.proxy);
                    require(rowBindings.length==9,"Missing row 4-9 bindings");
                    for(net.minecraft.client.settings.KeyBinding binding:rowBindings) require(binding.getKeyCode()==0,"New row binding is not unbound");
                    chestRowInitial=InventoryAdapter.snapshot(chestRowScreen.inventorySlots);
                    List<org.firebadnofire.storagetweaks.inventory.Stack> swapped=new ArrayList<>(chestRowInitial.slots);
                    for(int i=0;i<9;i++) Collections.swap(swapped,first+i,storageSize+27+i);
                    chestRowExpected=new Snapshot(swapped,chestRowInitial.cursor,chestRowInitial.limits());
                    chestSwapRepeat=0;dispatchChestRow(number,chestRowCase%2!=0);
                    stage=123;next=ticks+40;break;
                case 123:
                    require(!ClientProxy.JOB.active(),"Chest row swap did not finish");
                    require(InventoryAdapter.snapshot(mc.player.openContainer).equals(chestSwapRepeat==0?chestRowExpected:chestRowInitial),"Client chest swap differs from exact column exchange");
                    if(chestRowCase==5 || chestRowCase==14) net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"chest-rows-"+(chestRowCase==5?"small":"large")+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    pending=server(() -> {
                        Snapshot expectedRow=chestSwapRepeat==0?chestRowExpected:chestRowInitial;
                        require(player().inventory.getItemStack().isEmpty(),"Chest swap left server cursor occupied");
                        for(int i=0;i<expectedRow.slots.size();i++) require(expectedRow.slots.get(i).equals(InventoryAdapter.value(player().openContainer.getSlot(i).getStack())),"Server chest row mismatch at slot "+i);
                    });stage=124;break;
                case 124:
                    if(chestSwapRepeat==0) {
                        chestSwapRepeat=1;
                        int reverseNumber=chestRowCase<6?chestRowCase+1:chestRowCase-6+1;
                        dispatchChestRow(reverseNumber,chestRowCase%2==0);stage=123;next=ticks+40;break;
                    }
                    pass("CHEST_ROW_SWAP_SERVER_VERIFIED case="+chestRowCase);
                    mc.player.closeScreen();chestRowCase++;if(chestRowCase<15) {stage=120;break;}
                    Settings.chestButtons=true;chestRowsTested=true;
                    if(mode.equals("pack")) {stage=42;break;}
                    pass("ALL CHEST ROW SWAP TESTS PASSED");stage=125;mc.shutdown();break;
                case 100:
                    Field overrideField=ClientProxy.class.getDeclaredField("punchOverride");overrideField.setAccessible(true);
                    overrideBinding=(net.minecraft.client.settings.KeyBinding)overrideField.get(StorageTweaks.proxy);
                    if(overrideCase==0) require(overrideBinding.getKeyCode()==org.lwjgl.input.Keyboard.KEY_LSHIFT,"Override does not default to Left Shift");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.client.settings.KeyBinding.class,overrideBinding,false,"pressed","field_74513_e");
                    if(overrideCase>0) overrideBinding.setKeyCode(org.lwjgl.input.Keyboard.KEY_G);
                    Settings.punchExclusions=new String[]{"minecraft:diamond"};
                    pending=server(() -> {
                        player().setGameType(mode.contains("creative")?GameType.CREATIVE:GameType.SURVIVAL);
                        player().world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                        player().connection.setPlayerLocation(.5,4,.5,0,20);player().inventory.clear();player().inventory.currentItem=0;player().setSneaking(false);
                        player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());chest().clear();
                        net.minecraft.item.Item[] kinds={Items.DIAMOND_AXE,Items.DIAMOND_HOE,Items.DIAMOND_SWORD,Items.BOW,Items.SHEARS,Items.ELYTRA,Items.DIAMOND,Items.APPLE};
                        ItemStack held=new ItemStack(kinds[overrideCase],overrideCase<6?1:7);
                        if(overrideCase<6) {
                            held.setItemDamage(17);held.setStackDisplayName("Named protected item");
                            held.addEnchantment(net.minecraft.init.Enchantments.UNBREAKING,1);
                        }
                        overrideOriginal=InventoryAdapter.value(held);
                        player().inventory.setInventorySlotContents(0,held);player().inventoryContainer.detectAndSendChanges();
                        mc.getIntegratedServer().getCommandManager().executeCommand(player(),"punchdeposit true");
                    });stage=101;next=ticks+25;break;
                case 101:
                    LookCommand.lookAt(new Vec3d(CHEST).add(.5,.5,.5));
                    if(!ClientProxy.capabilityStatus.equals("available") || mc.currentScreen!=null || mc.objectMouseOver==null || !CHEST.equals(mc.objectMouseOver.getBlockPos())) {
                        if(ticks-next>160) throw new AssertionError("Override fixture did not reach chest/capability");return;
                    }
                    java.lang.reflect.Method hintMethod=ClientProxy.class.getDeclaredMethod("overrideHint");hintMethod.setAccessible(true);
                    require(hintMethod.invoke(StorageTweaks.proxy).equals(overrideCase==0?"[SHIFT]":"[G]"),"Wrong rebound override hint");
                    require(PunchNetwork.eligible(ItemStack.EMPTY,true)==false,"Override permits empty hand");
                    require(PunchNetwork.requiresOverride(mc.player.getHeldItemMainhand())==(overrideCase<7),"Protected item classification wrong");
                    stage=102;next=ticks+2;break;
                case 102:
                    java.lang.reflect.Method hintAvailable=ClientProxy.class.getDeclaredMethod("available");hintAvailable.setAccessible(true);
                    if(!(boolean)hintAvailable.invoke(StorageTweaks.proxy)) {
                        if(ticks-next>120) throw new AssertionError("No protected-item hint before override held");return;
                    }
                    if(overrideCase<2) net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"tool-override-hint-"+overrideCase+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    net.minecraftforge.client.event.MouseEvent plainToolClick=new net.minecraftforge.client.event.MouseEvent();
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,plainToolClick,0,"button");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,plainToolClick,true,"buttonstate");
                    ((ClientProxy)StorageTweaks.proxy).mouse(plainToolClick);
                    require(plainToolClick.isCanceled()==(overrideCase==7),"Plain tool attack was intercepted or ordinary item was blocked");
                    if(overrideCase<7) {
                        Field nextRequest=ClientProxy.class.getDeclaredField("request");nextRequest.setAccessible(true);
                        long protectedRequest=nextRequest.getLong(StorageTweaks.proxy)+1;nextRequest.setLong(StorageTweaks.proxy,protectedRequest);
                        punch(protectedRequest,CHEST); // Fresh, matching-slot request without override must still be refused.
                    }
                    stage=103;next=ticks+15;break;
                case 103:
                    pending=server(() -> {
                        if(overrideCase<7) {
                            require(overrideOriginal.equals(InventoryAdapter.value(player().getHeldItemMainhand())),"Server accepted protected item without explicit override");
                            require(chest().isEmpty(),"Rejected protected request changed chest");
                        }
                    });stage=104;break;
                case 104:
                    if(overrideCase<7) {
                        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.client.settings.KeyBinding.class,overrideBinding,true,"pressed","field_74513_e");
                        net.minecraftforge.client.event.MouseEvent overrideClick=new net.minecraftforge.client.event.MouseEvent();
                        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,overrideClick,0,"button");
                        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,overrideClick,true,"buttonstate");
                        ((ClientProxy)StorageTweaks.proxy).mouse(overrideClick);
                        require(overrideClick.isCanceled(),"Override click fell through to mining");
                    }
                    stage=105;next=ticks+20;break;
                case 105:
                    pending=server(() -> {
                        require(player().getHeldItemMainhand().isEmpty(),"Override did not deposit held item");
                        require(overrideOriginal.equals(InventoryAdapter.value(chest().getStackInSlot(0))),"Override changed count, name, damage or enchantment");
                        require(player().world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"Override destroyed chest");
                        require(!player().isSneaking(),"Rebound override should not require vanilla crouch state");
                    });stage=106;break;
                case 106:
                    pass("TOOL_OVERRIDE_SERVER_VERIFIED case="+overrideCase+" mode="+mode);
                    overrideCase++;if(overrideCase<8) {stage=100;break;}
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.client.settings.KeyBinding.class,overrideBinding,false,"pressed","field_74513_e");
                    overrideBinding.setKeyCode(org.lwjgl.input.Keyboard.KEY_LSHIFT);Settings.punchExclusions=new String[0];
                    pass("ALL TOOL OVERRIDE TESTS PASSED mode="+mode);stage=107;mc.shutdown();break;
                case 90:
                    pending=server(() -> {
                        player().setGameType(mode.contains("creative")?GameType.CREATIVE:GameType.SURVIVAL);
                        player().world.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
                        player().world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                        player().connection.setPlayerLocation(.5,4,.5,0,20);player().inventory.clear();
                        player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());chest().clear();
                        for(int i=0;i<9;i++) player().inventory.setInventorySlotContents(i,new ItemStack(Items.APPLE,i+2));
                        player().inventoryContainer.detectAndSendChanges();
                        mc.getIntegratedServer().getCommandManager().executeCommand(player(),"punchdeposit true");
                    });stage=91;next=ticks+30;break;
                case 91:
                    if(!ClientProxy.capabilityStatus.equals("available")) {
                        if(ticks-next>150) throw new AssertionError("Punch capability unavailable: "+ClientProxy.capabilityStatus);
                        return;
                    }
                    LookCommand.lookAt(new Vec3d(CHEST).add(.5,.5,.5));
                    pending=mc.getConnection().getNetworkManager().channel().eventLoop().submit(() -> {
                        mc.getConnection().getNetworkManager().channel().pipeline().addFirst("delayed-punch-label",new ChannelInboundHandlerAdapter() {
                            @Override public void channelRead(ChannelHandlerContext context,Object message) throws Exception {
                                io.netty.buffer.ByteBuf probeData=null;String probeChannel=null;
                                if(message instanceof net.minecraft.network.play.server.SPacketCustomPayload) {
                                    net.minecraft.network.play.server.SPacketCustomPayload packet=(net.minecraft.network.play.server.SPacketCustomPayload)message;
                                    probeData=packet.getBufferData();probeChannel=packet.getChannelName();
                                } else if(message instanceof net.minecraftforge.fml.common.network.internal.FMLProxyPacket) {
                                    net.minecraftforge.fml.common.network.internal.FMLProxyPacket packet=(net.minecraftforge.fml.common.network.internal.FMLProxyPacket)message;
                                    probeData=packet.payload();probeChannel=packet.channel();
                                }
                                if("fbstorage".equals(probeChannel) && probeData.readableBytes()>1 && probeData.getUnsignedByte(probeData.readerIndex()+1)==6) {
                                    delayedProbeReplies.incrementAndGet();
                                    context.executor().schedule(() -> context.fireChannelRead(message),2,java.util.concurrent.TimeUnit.SECONDS);return;
                                }
                                super.channelRead(context,message);
                            }
                        });
                    });stage=92;next=ticks+15;break;
                case 92:
                    if(mc.currentScreen!=null || mc.objectMouseOver==null || !CHEST.equals(mc.objectMouseOver.getBlockPos())) {
                        if(ticks-next>100) throw new AssertionError("Rapid fixture not aiming at chest: screen="+mc.currentScreen+" hit="+mc.objectMouseOver+" held="+mc.player.getHeldItemMainhand());
                        return;
                    }
                    if(rapidIndex<9) {
                        mc.player.inventory.currentItem=rapidIndex;
                        // Remove any previously received label authorization. Click in this same
                        // frame, before a slot-change tick or its availability reply can run.
                        Field auth=ClientProxy.class.getDeclaredField("authorizedTarget");auth.setAccessible(true);auth.set(StorageTweaks.proxy,null);
                        if(rapidIndex%2==0) {
                            net.minecraftforge.client.event.MouseEvent rapidMouse=new net.minecraftforge.client.event.MouseEvent();
                            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,rapidMouse,0,"button");
                            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,rapidMouse,true,"buttonstate");
                            ((ClientProxy)StorageTweaks.proxy).mouse(rapidMouse);
                            if(!rapidMouse.isCanceled()) mc.playerController.clickBlock(CHEST,EnumFacing.NORTH);
                            require(rapidMouse.isCanceled(),"Switched-slot attack fell through to mining at slot "+rapidIndex+" capability="+ClientProxy.capabilityStatus+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" hit="+mc.objectMouseOver);
                        } else {
                            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),true);
                            mc.playerController.updateController(); // Our mouse/key interception synchronizes before any attack.
                            mc.playerController.clickBlock(CHEST,EnumFacing.NORTH);
                            require(!mc.gameSettings.keyBindAttack.isKeyDown(),"Remapped/held attack survived deposit interception");
                        }
                        require(mc.world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"Creative client predicted chest destruction");
                        rapidIndex++;next=ticks+1;return;
                    }
                    stage=93;next=ticks+30;break;
                case 93:
                    pending=server(() -> {
                        require(player().world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"Rapid attacks destroyed server chest");
                        require(total(chest(),Items.APPLE)==54,"Rapid deposits did not conserve all 54 apples: "+total(chest(),Items.APPLE));
                        for(int i=0;i<9;i++) require(player().inventory.getStackInSlot(i).isEmpty(),"Rapid deposit missed slot "+i);
                        player().inventory.setInventorySlotContents(0,new ItemStack(Items.APPLE,3));player().inventoryContainer.detectAndSendChanges();
                    });stage=94;next=ticks+20;break;
                case 94:
                    mc.player.inventory.currentItem=0;
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),true);
                    net.minecraft.client.settings.KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                    ((ClientProxy)StorageTweaks.proxy).tick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
                    require(!mc.gameSettings.keyBindAttack.isKeyDown() && !mc.gameSettings.keyBindAttack.isPressed(),"Held attack was not suppressed");
                    require(mc.world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"Held attack destroyed chest");
                    require(delayedProbeReplies.get()>0,"Probe latency fixture did not intercept any real replies");
                    pass("IMMEDIATE_RAPID_PUNCH_PASSED mode="+mode+" delayedProbeReplies="+delayedProbeReplies.get());
                    Settings.punch=false;
                    net.minecraftforge.client.event.MouseEvent disabledPunch=new net.minecraftforge.client.event.MouseEvent();
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,disabledPunch,0,"button");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,disabledPunch,true,"buttonstate");
                    ((ClientProxy)StorageTweaks.proxy).mouse(disabledPunch);
                    require(!disabledPunch.isCanceled(),"Disabled client punch intercepted ordinary attack");
                    stage=95;next=ticks+20;break;
                case 95:
                    pending=server(() -> {
                        net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock disabledAttack=
                            new net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(player(),CHEST,EnumFacing.NORTH,new Vec3d(CHEST));
                        MinecraftForge.EVENT_BUS.post(disabledAttack);
                        require(!disabledAttack.isCanceled(),"Disabled client punch left server break guard enabled");
                    });stage=96;break;
                case 96:
                    Settings.punch=true;
                    pass("CLIENT_PUNCH_DISABLE_RESTORES_SERVER_MINING mode="+mode);
                    stage=97;mc.shutdown();break;
                case 80:
                    if(mc.currentScreen!=null) mc.player.closeScreen();
                    hotbar=null;
                    pending=server(() -> {
                        player().setGameType(rowVariant%6<3?GameType.SURVIVAL:GameType.CREATIVE);
                        player().inventory.clear();
                        for(int i=0;i<36;i++) {
                            ItemStack stack=i%7==0?ItemStack.EMPTY:new ItemStack(Items.APPLE,1+i);
                            if(i%5==0 && !stack.isEmpty()) stack.setStackDisplayName("Named "+i);
                            player().inventory.setInventorySlotContents(i,stack);
                        }
                        player().inventoryContainer.detectAndSendChanges();
                    });stage=81;next=ticks+30;break;
                case 81:
                    if(rowVariant>=6) {
                        rowExpected=new ArrayList<>();
                        for(int i=0;i<36;i++) rowExpected.add(mc.player.inventory.getStackInSlot(i).copy());
                        int keyRow=rowVariant%3+1, keyFirst=9+(3-keyRow)*9;
                        for(int i=0;i<9;i++) Collections.swap(rowExpected,i,keyFirst+i);
                        Field bindings=ClientProxy.class.getDeclaredField("swapRows");bindings.setAccessible(true);
                        net.minecraft.client.settings.KeyBinding[] keys=(net.minecraft.client.settings.KeyBinding[])bindings.get(StorageTweaks.proxy);
                        for(net.minecraft.client.settings.KeyBinding key:keys) require(key.getKeyCode()==0,"Swap binding must initially be unbound");
                        Set<Integer> usedKeys=new HashSet<>();
                        for(net.minecraft.client.settings.KeyBinding key:mc.gameSettings.keyBindings) {
                            usedKeys.add(key.getKeyCode());
                            if(key.getKeyCode()==org.lwjgl.input.Keyboard.KEY_F10) StorageTweaks.LOG.info("ROW_FIXTURE_F10_BINDING {}",key.getKeyDescription());
                        }
                        int unusedKey=org.lwjgl.input.Keyboard.KEY_F15;
                        while(unusedKey>0 && usedKeys.contains(unusedKey)) unusedKey--;
                        require(unusedKey>0,"No unused keyboard key for fixture");
                        StorageTweaks.LOG.info("ROW_FIXTURE_UNUSED_KEY {}",unusedKey);
                        keys[keyRow-1].setKeyCode(unusedKey);
                        net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
                        net.minecraft.client.settings.KeyBinding.onTick(unusedKey);
                        stage=82;next=ticks+50;break;
                    }
                    GuiContainer rowScreen;
                    if(rowVariant<3) rowScreen=new net.minecraft.client.gui.inventory.GuiInventory(mc.player);
                    else {
                        net.minecraft.client.gui.inventory.GuiContainerCreative creativeRows=new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.player);
                        mc.displayGuiScreen(creativeRows);
                        java.lang.reflect.Method selectRows=null;
                        for(java.lang.reflect.Method method:creativeRows.getClass().getDeclaredMethods())
                            if(java.lang.reflect.Modifier.isPrivate(method.getModifiers()) && method.getReturnType()==Void.TYPE &&
                                Arrays.equals(method.getParameterTypes(),new Class<?>[]{net.minecraft.creativetab.CreativeTabs.class})) selectRows=method;
                        require(selectRows!=null,"Creative tab method unavailable");selectRows.setAccessible(true);
                        selectRows.invoke(creativeRows,net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS);
                        for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.matches("[123]")) {
                            button.drawButton(mc,0,0,0);require(!button.visible,"Row swap visible in creative catalogue");
                        }
                        selectRows.invoke(creativeRows,net.minecraft.creativetab.CreativeTabs.INVENTORY);
                        rowScreen=creativeRows;
                    }
                    mc.displayGuiScreen(rowScreen);
                    rowExpected=new ArrayList<>();
                    for(int i=0;i<36;i++) rowExpected.add(mc.player.inventory.getStackInSlot(i).copy());
                    int rowNumber=rowVariant%3+1, firstRow=9+(3-rowNumber)*9;
                    for(int i=0;i<9;i++) Collections.swap(rowExpected,i,firstRow+i);
                    int rowButtonCount=0;
                    for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.matches("[123]")) {
                        rowButtonCount++;require(button.x<rowScreen.getGuiLeft(),"Swap button is not left of inventory");
                        if(button.displayString.equals(Integer.toString(rowNumber)))
                            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre(rowScreen,button,buttons));
                    }
                    require(rowButtonCount==3,"Expected exactly three row buttons, none for hotbar");
                    require(ClientProxy.JOB.active(),"Row swap did not start");stage=82;next=ticks+50;break;
                case 82:
                    if(rowVariant<6) net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"row-swap-"+rowVariant+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    require(!ClientProxy.JOB.active(),"Row swap failed to finish");
                    for(int i=0;i<36;i++) require(ItemStack.areItemStacksEqual(rowExpected.get(i),mc.player.inventory.getStackInSlot(i)),"Client row mismatch "+i);
                    pending=server(() -> {
                        for(int i=0;i<36;i++) require(ItemStack.areItemStacksEqual(rowExpected.get(i),player().inventory.getStackInSlot(i)),"Server row mismatch "+i);
                        require(player().inventory.getItemStack().isEmpty(),"Row swap left cursor occupied");
                    });stage=83;break;
                case 83:
                    pass("ROW_SWAP_SERVER_VERIFIED variant="+rowVariant);mc.player.closeScreen();
                    Field resetBindings=ClientProxy.class.getDeclaredField("swapRows");resetBindings.setAccessible(true);
                    for(net.minecraft.client.settings.KeyBinding key:(net.minecraft.client.settings.KeyBinding[])resetBindings.get(StorageTweaks.proxy)) key.setKeyCode(0);
                    net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();rowVariant++;
                    if(rowVariant<12) { stage=80;break; }
                    rowsTested=true;
                    if(mode.equals("pack")) { stage=42;break; }
                    pass("ALL ROW SWAP RUNTIME TESTS PASSED");stage=84;mc.shutdown();break;
                case 70:
                    java.lang.reflect.Method hotbarAvailability=ClientProxy.class.getDeclaredMethod("available");hotbarAvailability.setAccessible(true);
                    if(!(boolean)hotbarAvailability.invoke(StorageTweaks.proxy)) {
                        if(ticks-next>20) throw new AssertionError("Hotbar switch left availability stuck while still aiming at chest; held="+mc.player.getHeldItemMainhand());return;
                    }
                    require(mc.objectMouseOver.getBlockPos().equals(CHEST),"Hotbar test lost chest aim");
                    net.minecraftforge.client.event.MouseEvent hotbarMouse=new net.minecraftforge.client.event.MouseEvent();
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,hotbarMouse,0,"button");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,hotbarMouse,true,"buttonstate");
                    ((ClientProxy)StorageTweaks.proxy).mouse(hotbarMouse);
                    require(hotbarMouse.isCanceled(),"Switched-slot punch did not intercept");stage=71;next=ticks+20;break;
                case 71:
                    pending=server(() -> {
                        require(chest().getStackInSlot(0).getItem()==Items.APPLE && chest().getStackInSlot(0).getCount()==7,"Switched hotbar deposit failed");
                        require(player().inventory.getStackInSlot(1).isEmpty(),"Selected hotbar slot not consumed");
                        require(player().inventory.getStackInSlot(0).getCount()==10,"Unselected hotbar slot changed");
                    });stage=72;break;
                case 72:
                    pass("HOTBAR_SWITCH_DEPOSIT_WITHOUT_LOOKING_AWAY mode="+mode);stage=73;mc.shutdown();break;
                case 60:
                    pending=server(() -> {
                        if(mode.endsWith("creative")) player().setGameType(GameType.CREATIVE);
                        player().connection.setPlayerLocation(.5,4,.5,0,20);player().inventory.clear();
                        player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());chest().clear();
                        player().inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,10));
                        if(mode.contains("hotbar")) player().inventory.setInventorySlotContents(1,new ItemStack(Items.APPLE,7));
                        player().inventoryContainer.detectAndSendChanges();
                        try { mc.getIntegratedServer().getCommandManager().executeCommand(player(),"punchdeposit true"); }
                        catch(Exception failure) { throw new AssertionError(failure); }
                    });stage=61;next=ticks+40;break;
                case 61:
                    java.lang.reflect.Method availability=ClientProxy.class.getDeclaredMethod("available");availability.setAccessible(true);
                    if(!(boolean)availability.invoke(StorageTweaks.proxy)) {
                        if(ticks-next>120) throw new AssertionError("No initial punch availability: "+ClientProxy.capabilityStatus+" held="+mc.player.getHeldItemMainhand()+" hit="+mc.objectMouseOver);
                        return;
                    }
                    if(mode.contains("hotbar")) { earlyHotbarSwitch=true;stage=70;next=ticks+1;break; }
                    pending=server(() -> mc.getIntegratedServer().getCommandManager().executeCommand(player(),"punchdeposit false"));stage=62;next=ticks+20;break;
                case 62:
                    require(!ClientProxy.capabilityStatus.equals("available"),"Disable did not revoke capability");
                    pending=server(() -> mc.getIntegratedServer().getCommandManager().executeCommand(player(),"punchdeposit true"));stage=63;next=ticks+20;break;
                case 63:
                    java.lang.reflect.Method enabledAvailability=ClientProxy.class.getDeclaredMethod("available");enabledAvailability.setAccessible(true);
                    if(!(boolean)enabledAvailability.invoke(StorageTweaks.proxy)) {
                        if(ticks-next>120) throw new AssertionError("Punch did not recover after enable: "+ClientProxy.capabilityStatus);
                        return;
                    }
                    net.minecraftforge.client.event.MouseEvent punchMouse=new net.minecraftforge.client.event.MouseEvent();
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,punchMouse,0,"button");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,punchMouse,true,"buttonstate");
                    ((ClientProxy)StorageTweaks.proxy).mouse(punchMouse);
                    require(punchMouse.isCanceled(),"Available punch did not intercept mouse");stage=64;next=ticks+20;break;
                case 64:
                    pending=server(() -> {
                        require(chest().getStackInSlot(0).getCount()==10,"Mouse-hook deposit failed after toggle");
                        require(player().getHeldItemMainhand().isEmpty(),"Deposit did not consume held stack");
                    });stage=65;break;
                case 65:
                    pass("PUNCH_RECOVERY_PASSED: available, disabled, reenabled, mouse-hook deposit server verified");
                    pending=server(() -> { player().inventory.setInventorySlotContents(0,new ItemStack(Items.APPLE,7));player().inventoryContainer.detectAndSendChanges(); });
                    stage=66;next=ticks+15;break;
                case 66:
                    java.lang.reflect.Method switchedAvailability=ClientProxy.class.getDeclaredMethod("available");switchedAvailability.setAccessible(true);
                    if(!(boolean)switchedAvailability.invoke(StorageTweaks.proxy)) {
                        if(ticks-next>120) throw new AssertionError("Punch unavailable after held-item switch");return;
                    }
                    net.minecraftforge.client.event.MouseEvent switchedMouse=new net.minecraftforge.client.event.MouseEvent();
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,switchedMouse,0,"button");
                    net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.client.event.MouseEvent.class,switchedMouse,true,"buttonstate");
                    ((ClientProxy)StorageTweaks.proxy).mouse(switchedMouse);stage=67;next=ticks+20;break;
                case 67:
                    pending=server(() -> {
                        require(chest().getStackInSlot(0).getCount()==10 && chest().getStackInSlot(1).getItem()==Items.APPLE && chest().getStackInSlot(1).getCount()==7,"Switched-item punch failed");
                        require(player().getHeldItemMainhand().isEmpty(),"Switched-item punch left held contents");
                    });stage=68;break;
                case 68:
                    pass("PUNCH_SWITCH_RECOVERY_PASSED: switched item deposited after toggle, mode="+mode);stage=69;mc.shutdown();break;
                case 56:
                    pending=server(() -> {
                        player().connection.setPlayerLocation(.5,4,.5,0,20);player().inventory.clear();
                        int[] counts={3,12,7};BlockPos[] positions={new BlockPos(0,4,3),new BlockPos(2,4,1),new BlockPos(-2,4,1)};
                        for(int i=0;i<positions.length;i++) {
                            player().world.setBlockState(positions[i],Blocks.CHEST.getDefaultState());
                            net.minecraft.tileentity.TileEntityChest tile=(net.minecraft.tileentity.TileEntityChest)player().world.getTileEntity(positions[i]);
                            tile.clear();tile.setInventorySlotContents(0,new ItemStack(Items.APPLE,counts[i]));
                        }
                        player().inventoryContainer.detectAndSendChanges();
                    });stage=57;next=ticks+50;break;
                case 57:
                    if(mc.player.getDistance(.5,4,.5)>1 || mc.world.getBlockState(new BlockPos(2,4,1)).getBlock()!=Blocks.CHEST) return;
                    mc.displayGuiScreen(null);
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:apple");
                    scanYaw=mc.player.rotationYaw;scanPitch=mc.player.rotationPitch;
                    stage=58;next=ticks+1;break;
                case 58:
                    if(ClientProxy.finder.interacting()) {
                        require(mc.player.rotationYaw==scanYaw && mc.player.rotationPitch==scanPitch,"Item scan moved view before final selection");return;
                    }
                    require(mc.currentScreen instanceof GuiContainer,"Best item search did not show a container");
                    GuiContainer bestScreen=(GuiContainer)mc.currentScreen;
                    int apples=0;
                    for(int slot:InventoryAdapter.group(bestScreen.inventorySlots,false,false)) if(bestScreen.inventorySlots.getSlot(slot).getStack().getItem()==Items.APPLE) apples+=bestScreen.inventorySlots.getSlot(slot).getStack().getCount();
                    require(apples==12,"Did not select highest count: "+apples);
                    Vec3d towardBest=new Vec3d(2.5,4.5,1.5).subtract(mc.player.getPositionEyes(1)).normalize();
                    require(mc.player.getLookVec().normalize().dotProduct(towardBest)>.999999,"Did not face best chest");
                    require(!ClientProxy.finder.interacting(),"Best search leaked suppression");
                    mc.player.closeScreen();
                    java.util.List<net.minecraft.client.gui.ChatLine> history=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiNewChat.class,mc.ingameGUI.getChatGUI(),"chatLines","field_146252_h");
                    net.minecraft.util.text.ITextComponent clickPart=null;
                    for(net.minecraft.client.gui.ChatLine line:history) {
                        for(net.minecraft.util.text.ITextComponent wrapped:net.minecraft.client.gui.GuiUtilRenderComponents.splitText(line.getChatComponent(),70,mc.fontRenderer,false,false))
                            for(net.minecraft.util.text.ITextComponent part:wrapped) {
                                net.minecraft.util.text.event.ClickEvent click=part.getStyle().getClickEvent();
                                if(click!=null && click.getAction()==net.minecraft.util.text.event.ClickEvent.Action.RUN_COMMAND && click.getValue().startsWith("/clook ")) clickPart=part;
                            }
                    }
                    require(clickPart!=null,"Wrapped chat lost Look at click action");
                    String[] clickCoordinates=clickPart.getStyle().getClickEvent().getValue().split(" ");
                    net.minecraft.client.gui.GuiChat clickChat=new net.minecraft.client.gui.GuiChat();mc.displayGuiScreen(clickChat);
                    require(clickChat.handleComponentClick(clickPart),"Chat did not handle Look at click");
                    Vec3d clickedTarget=new Vec3d(Integer.parseInt(clickCoordinates[1])+.5,Integer.parseInt(clickCoordinates[2])+.5,Integer.parseInt(clickCoordinates[3])+.5);
                    require(mc.player.getLookVec().normalize().dotProduct(clickedTarget.subtract(mc.player.getPositionEyes(1)).normalize())>.999999,"Look at click did not run client command");
                    pass("Wrapped Look at chat click executes clook; scan leaves view unchanged until completion");
                    pass("BEST_ITEM_SEARCH_PASSED: scanned three chests, opened 12 apples and faced best chest");
                    stage=59;mc.shutdown();break;
                case 50:
                    mc.displayGuiScreen(null);
                    pending=server(() -> {
                        player().setGameType(GameType.CREATIVE);player().inventory.clear();
                        player().inventory.setInventorySlotContents(9,new ItemStack(Blocks.STONE,12));
                        player().inventory.setInventorySlotContents(10,new ItemStack(Items.APPLE,7));
                        player().inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage=51;next=ticks+30;break;
                case 51:
                    net.minecraft.client.gui.inventory.GuiContainerCreative creative=new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.player);
                    mc.displayGuiScreen(creative);
                    for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.equals("Sort")) {
                        require(button.x>=creative.getGuiLeft()+creative.getXSize(),"Creative Sort is not on the right");
                        if(!InventoryAdapter.creative(creative)) require(!button.visible,"Creative Sort visible in catalogue");
                    }
                    java.lang.reflect.Method select=null;
                    for(java.lang.reflect.Method method:creative.getClass().getDeclaredMethods())
                        if(java.lang.reflect.Modifier.isPrivate(method.getModifiers()) && method.getReturnType()==Void.TYPE &&
                                java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{net.minecraft.creativetab.CreativeTabs.class})) {
                            require(select==null,"Ambiguous creative tab fixture method");select=method;
                        }
                    require(select!=null,"Creative tab fixture method missing");select.setAccessible(true);
                    select.invoke(creative,net.minecraft.creativetab.CreativeTabs.INVENTORY);
                    start(2);stage=52;next=ticks+40;break;
                case 52:
                    require(!ClientProxy.JOB.active(),"Creative sort did not finish");
                    for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.equals("Sort"))
                        require(button.visible,"Creative Sort hidden in player inventory");
                    pending=server(() -> {
                        require(player().inventory.getStackInSlot(9).getItem()==Items.APPLE,"Creative sort server order wrong");
                        require(player().inventory.getStackInSlot(10).getCount()==12,"Creative sort lost stone");
                        require(player().inventory.getStackInSlot(0).getItem()==Items.DIAMOND && player().inventory.getStackInSlot(0).getCount()==3,"Creative sort changed hotbar");
                    });stage=53;break;
                case 53:
                    pass("Creative main-inventory sort accepted by integrated server; hotbar preserved");
                    mc.player.closeScreen();
                    mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
                    ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:apple");
                    require(ClientProxy.finder.interacting(),"Chat rejected item search");
                    ClientProxy.finder.cancel("Fixture complete");mc.displayGuiScreen(null);
                    pass("Item search accepts command chat and releases cancellation suppression");
                    net.minecraft.util.math.Vec3d beforeLook=mc.player.getPositionVector();
                    net.minecraft.util.math.BlockPos lookTarget=mc.player.getPosition().add(10,2,-7);
                    ClientCommandHandler.instance.executeCommand(mc.player,"/clook "+lookTarget.getX()+" "+lookTarget.getY()+" "+lookTarget.getZ());
                    net.minecraft.util.math.Vec3d expectedLook=new net.minecraft.util.math.Vec3d(lookTarget).add(.5,.5,.5).subtract(mc.player.getPositionEyes(1)).normalize();
                    StorageTweaks.LOG.info("LOOK_FIXTURE target={} yaw={} pitch={} head={} actual={} expected={} command={}",lookTarget,mc.player.rotationYaw,mc.player.rotationPitch,mc.player.rotationYawHead,mc.player.getLookVec(),expectedLook,ClientCommandHandler.instance.getCommands().get("clook"));
                    require(mc.player.getLookVec().normalize().dotProduct(expectedLook)>.999999,"clook did not aim at block center");
                    require(mc.player.getPositionVector().equals(beforeLook),"clook moved player");
                    pass("clook aims at block center without moving player");
                    pending=server(() -> {
                        net.minecraft.command.ICommand command=new org.firebadnofire.storagetweaks.server.PunchDepositCommand();
                        require(command.checkPermission(mc.getIntegratedServer(),player()),"Singleplayer owner requires OP");
                        try {
                            command.execute(mc.getIntegratedServer(),player(),new String[]{"false"});
                            require(!Settings.serverPunch,"Server command did not disable punch");
                            require(!Settings.config.get("server","enabled",true).getBoolean(),"Server toggle was not persisted");
                            command.execute(mc.getIntegratedServer(),player(),new String[]{"true"});
                            require(Settings.serverPunch,"Server command did not enable punch");
                        } catch(net.minecraft.command.CommandException failure) { throw new AssertionError(failure); }
                    });stage=54;break;
                case 54:
                    pass("Singleplayer punchdeposit toggle and persisted setting");
                    pass("ALL INTEGRATED RUNTIME TESTS PASSED");stage=55;mc.shutdown();break;
                case 43:
                    if(punchRestrictions()) stage=44;
                    break;
                case 44:
                    pending=server(() -> {
                        ((net.minecraft.inventory.IInventory)player().world.getTileEntity(CHEST)).clear();
                        player().world.setBlockState(CHEST,Blocks.AIR.getDefaultState());
                        player().world.setBlockState(CHEST.up(),Blocks.AIR.getDefaultState());
                        player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());
                        player().connection.setPlayerLocation(.5,4,.5,0,20);
                        for(int slot=0;slot<27;slot++) chest().setInventorySlotContents(slot,new ItemStack(Blocks.STONE,64));
                        for(int slot=0;slot<36;slot++) player().inventory.setInventorySlotContents(slot,new ItemStack(Blocks.STONE,64));
                        player().inventoryContainer.detectAndSendChanges();
                    });stage++;break;
                case 45:
                    if(mc.currentScreen!=null || mc.player.getDistanceSq(CHEST)>16) return;
                    mc.getConnection().getNetworkManager().channel().pipeline().addBefore("storage_tweaks_observer","storage_test_delay_contents",new ChannelDuplexHandler() {
                        @Override public void channelRead(ChannelHandlerContext context,Object message) throws Exception {
                            if(!(message instanceof net.minecraft.network.play.server.SPacketWindowItems) ||
                                    ((net.minecraft.network.play.server.SPacketWindowItems)message).getWindowId()==0) super.channelRead(context,message);
                        }
                    });
                    suppressed=null;ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:bedrock");
                    stage++;next=ticks+5;break;
                case 46:
                    if(suppressed==null || mc.player.openContainer!=suppressed.inventorySlots) {
                        require(ticks<next+80,"Cursor fixture did not receive a suppressed container");return;
                    }
                    pending=server(() -> {
                        ItemStack cursor=new ItemStack(Items.DIAMOND,2);player().inventory.setItemStack(cursor);
                        player().connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-1,-1,cursor.copy()));
                    });stage++;break;
                case 47:
                    require(!ClientProxy.finder.interacting(),"Cursor update left finder active");
                    require(mc.currentScreen==suppressed && mc.player.openContainer==suppressed.inventorySlots,"Cursor recovery replaced/closed the suppressed window");
                    require(mc.player.inventory.getItemStack().getItem()==Items.DIAMOND && mc.player.inventory.getItemStack().getCount()==2,"Finder cleanup lost client cursor");
                    mc.getConnection().getNetworkManager().channel().pipeline().remove("storage_test_delay_contents");
                    pending=server(() -> {
                        require(player().openContainer!=player().inventoryContainer && player().inventory.getItemStack().getCount()==2,"Finder cleanup closed server window or lost cursor");
                        chest().setInventorySlotContents(0,ItemStack.EMPTY);player().openContainer.detectAndSendChanges();
                    });stage++;break;
                case 48:
                    require(mc.player.openContainer.getSlot(0).getStack().isEmpty(),"Cursor recovery slot not synchronized");
                    mc.playerController.windowClick(mc.player.openContainer.windowId,0,0,net.minecraft.inventory.ClickType.PICKUP,mc.player);
                    stage++;next=ticks+15;break;
                case 49:
                    require(mc.player.inventory.getItemStack().isEmpty(),"Normal recovery click did not empty cursor");
                    pending=server(() -> {
                        require(player().inventory.getItemStack().isEmpty() && total(chest(),Items.DIAMOND)==2,"Cursor recovery did not conserve server items");
                        for(int slot=0;slot<36;slot++) require(player().inventory.getStackInSlot(slot).getCount()==64,"Cursor recovery changed full player inventory");
                    });
                    mc.player.closeScreen();pass("Finder reveals original window for nonempty cursor instead of dropping it; normal recovery conserves items");stage=40;break;
                default:break;
            }
        } catch(Throwable failure) {
            failed=true;StorageTweaks.LOG.error("STORAGE_TWEAKS_RUNTIME_TEST_FAILED stage="+stage,failure);mc.shutdown();
        }
    }
    private void vanilla() {
        if(!launched) {
            if(mc.currentScreen instanceof GuiMainMenu && ticks>40) {
                launched=true;started=ticks;
                mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(),mc,"127.0.0.1",25590));
            }
            return;
        }
        if(ticks-started>1800) throw new AssertionError("Vanilla connection test timed out at stage "+stage);
        if(mc.player==null || mc.world==null || ticks<next) return;
        switch(stage) {
            case 0:
                pass("Modded client connected to vanilla server without server mod");
                stage++;next=ticks+200;break;
            case 1:
                require(!ClientProxy.capabilityStatus.equals("available"),"Punch activated without server support");
                require(mc.world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"Vanilla test chest fixture absent");
                mc.playerController.processRightClickBlock(mc.player,mc.world,CHEST,EnumFacing.NORTH,new Vec3d(.5,4.5,3),EnumHand.MAIN_HAND);
                stage++;next=ticks+20;break;
            case 2:require(mc.currentScreen instanceof GuiContainer,"Vanilla chest did not open");start(0);stage++;break;
            case 3:
                if(ClientProxy.JOB.active()) return;
                require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Vanilla Take mismatch");
                pass("Take acknowledged by vanilla server");start(1);stage++;break;
            case 4:
                if(ClientProxy.JOB.active()) return;
                require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Vanilla Store mismatch");
                pass("Store acknowledged by vanilla server");start(2);stage++;break;
            case 5:
                if(ClientProxy.JOB.active()) return;
                require(InventoryAdapter.snapshot(mc.player.openContainer).equals(expected),"Vanilla Sort mismatch");
                pass("Sort acknowledged by vanilla server");mc.player.closeScreen();
                ClientCommandHandler.instance.executeCommand(mc.player,"/cfinditem minecraft:apple");stage++;next=ticks+30;break;
            case 6:
                require(mc.currentScreen instanceof GuiContainer,"Vanilla item finder failed");
                require(!ClientProxy.finder.interacting(),"Vanilla screen suppression leak");
                pass("Client-only item search on vanilla server");
                pass("ALL VANILLA CONNECTION TESTS PASSED");stage++;mc.shutdown();break;
            default:break;
        }
    }
    private void lanHost() throws Exception {
        if(!launched) {
            if(mc.currentScreen instanceof GuiMainMenu && ticks>40) {
                launched=true;
                mc.launchIntegratedServer("lan-reproduction-"+System.currentTimeMillis(),"Storage Tweaks LAN reproduction",
                        new WorldSettings(713,GameType.SURVIVAL,false,false,WorldType.FLAT));
            }
            return;
        }
        if(mc.player==null || mc.world==null || mc.getIntegratedServer()==null) return;
        if(stage==0) {
            pending=server(() -> {
                player().getServerWorld().getGameRules().setOrCreateGameRule("doMobSpawning","false");
                player().getServerWorld().getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
                player().getServerWorld().getWorldInfo().setDifficultyLocked(true);
                player().connection.setPlayerLocation(8.5,4,.5,0,20);
                player().world.setBlockState(CHEST,Blocks.CHEST.getDefaultState());
                chest().clear();chest().setInventorySlotContents(0,new ItemStack(Blocks.STONE,20));
                chest().setInventorySlotContents(1,new ItemStack(Items.APPLE,28));
            });stage=1;
        } else if(stage==1 && pending.isDone()) {
            pending.get();pending=null;
            // Disposable LAN fixture uses offline test identities, never personal accounts.
            mc.getIntegratedServer().setOnlineMode(false);
            StorageTweaks.LOG.info("LAN_TEST_HOST_READY port={}",mc.getIntegratedServer().shareToLAN(mode.equals("lan-creative")?GameType.CREATIVE:GameType.SURVIVAL,false));stage=2;
        }
    }
    @SubscribeEvent public void lanJoin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        if(!(mode.equals("lan-host") || mode.equals("lan-creative")) || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP remote=(EntityPlayerMP)event.player;
        if(remote.connection.netManager.isLocalChannel()) return;
        remote.setGameType(mode.equals("lan-creative")?GameType.CREATIVE:GameType.SURVIVAL);
        remote.connection.setPlayerLocation(.5,4,.5,0,20);
        remote.inventory.clear();remote.inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,10));
        remote.inventoryContainer.detectAndSendChanges();
        remote.connection.netManager.channel().eventLoop().execute(() ->
            remote.connection.netManager.channel().pipeline().addBefore("packet_handler","storage_test_click_trace",new ChannelDuplexHandler() {
                @Override public void channelRead(ChannelHandlerContext context,Object message) throws Exception {
                    if(message instanceof CPacketClickWindow) {
                        CPacketClickWindow click=(CPacketClickWindow)message;
                        StorageTweaks.LOG.info("LAN_REMOTE_CLICK player={} window={} slot={} action={} type={}",remote.getName(),click.getWindowId(),click.getSlotId(),click.getActionNumber(),click.getClickType());
                    }
                    super.channelRead(context,message);
                }
            }));
        StorageTweaks.LOG.info("LAN_TEST_REMOTE_JOIN {}",remote.getName());
    }
    private void contention() throws Exception {
        if(!launched) {
            if(mc.currentScreen instanceof GuiMainMenu && ticks>40) {
                launched=true;started=ticks;
                mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(),mc,"127.0.0.1",25589));
            }
            return;
        }
        if(mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected || ticks-started>2400)
            throw new AssertionError("Dedicated contention join/fixture timed out at stage "+stage);
        if(mc.player==null || mc.world==null || ticks<next) return;
        switch(stage) {
            case 0:
                pass("CONTENTION_JOINED:"+mc.player.getName());stage++;break;
            case 1:
                if(!ClientProxy.capabilityStatus.equals("available") || mc.player.getHeldItemMainhand().getItem()!=Items.DIAMOND ||
                        mc.player.getHeldItemMainhand().getCount()!=7 || mc.world.getBlockState(CHEST).getBlock()!=Blocks.CHEST) return;
                stage++;next=ticks+120;break;
            case 2:punch(++request,CHEST);stage++;next=ticks+30;break;
            case 3:
                int remaining=mc.player.getHeldItemMainhand().getCount();
                require(remaining==6 || remaining==7,"Unexpected contested deposit remainder: "+remaining);
                require(mc.currentScreen==null,"Dedicated punch opened a GUI");
                pass("CONTENTION_REMAINDER:"+mc.player.getName()+":"+remaining);stage++;mc.shutdown();break;
            default:break;
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true)
    public void capture(net.minecraftforge.client.event.GuiOpenEvent event) {
        if(event.isCanceled() && event.getGui() instanceof GuiContainer && ClientProxy.finder!=null && ClientProxy.finder.interacting())
            suppressed=(GuiContainer)event.getGui();
    }
    @SubscribeEvent(receiveCanceled=true)
    public void traceOpen(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if(!event.getWorld().isRemote)
            StorageTweaks.LOG.info("FIXTURE_SERVER_OPEN target={} player={} sneaking={} canceled={} useBlock={}",
                    event.getPos(),event.getEntityPlayer().getPositionVector(),event.getEntityPlayer().isSneaking(),event.isCanceled(),event.getUseBlock());
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void captureButtons(net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post event) {
        if(event.getGui() instanceof GuiContainer) { buttonScreen=(GuiContainer)event.getGui();buttons=new ArrayList<>(event.getButtonList()); }
    }
    // Keep physical input in this disposable test window from changing deterministic fixtures.
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void ignoreMouse(net.minecraftforge.client.event.GuiScreenEvent.MouseInputEvent.Pre event) { if(event.getGui() instanceof GuiContainer) event.setCanceled(true); }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void ignoreKeyboard(net.minecraftforge.client.event.GuiScreenEvent.KeyboardInputEvent.Pre event) { if(event.getGui() instanceof GuiContainer) event.setCanceled(true); }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void ignoreWorldClicks(net.minecraftforge.client.event.MouseEvent event) { event.setCanceled(true); }
    private void start(int operation) {
        GuiContainer gui=(GuiContainer)mc.currentScreen;Snapshot snapshot=InventoryAdapter.snapshot(InventoryAdapter.container(gui));
        int[] storage=InventoryAdapter.group(InventoryAdapter.container(gui),false,false),player=InventoryAdapter.group(InventoryAdapter.container(gui),true,true);
        Plan plan=operation==0?Planner.transfer(snapshot,storage,player):operation==1?Planner.transfer(snapshot,player,storage):
                Planner.sort(snapshot,(gui instanceof net.minecraft.client.gui.inventory.GuiInventory || InventoryAdapter.creative(gui))?InventoryAdapter.group(InventoryAdapter.container(gui),true,false):storage,false);
        expected=plan.result;
        require(buttonScreen==gui,"No button initialization observed for current screen");
        String label=operation==0?"Take":operation==1?"Store":"Sort";
        net.minecraft.client.gui.GuiButton selected=null;
        for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.equals(label)) selected=button;
        require(selected!=null,"Missing "+label+" button at current GUI scale");
        require(selected.x>=0 && selected.y>=0 && selected.x+selected.width<=gui.width && selected.y+selected.height<=gui.height,"Inventory button outside screen bounds");
        net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre click=new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre(gui,selected,buttons);
        MinecraftForge.EVENT_BUS.post(click);
        require(click.isCanceled() && ClientProxy.JOB.active(),"Inventory button did not start its operation");next=ticks+2;
    }
    private boolean punchRestrictions() throws Exception {
        final int fixture=restrictionCase;
        final BlockPos distant=new BlockPos(1_000_000,4,1_000_000);
        if(restrictionPhase==0) {
            pending=server(() -> {
                player().connection.setPlayerLocation(.5,4,.5,0,20);
                for(BlockPos pos:new BlockPos[]{CHEST,CHEST.east()}) {
                    if(player().world.getTileEntity(pos) instanceof net.minecraft.inventory.IInventory)
                        ((net.minecraft.inventory.IInventory)player().world.getTileEntity(pos)).clear();
                    player().world.setBlockState(pos,Blocks.AIR.getDefaultState());
                }
                player().world.setBlockState(CHEST.up(),Blocks.AIR.getDefaultState());
                net.minecraft.block.Block block=fixture==9 || fixture==10?Blocks.ENDER_CHEST:
                        fixture==11?Blocks.WHITE_SHULKER_BOX:Blocks.CHEST;
                player().world.setBlockState(CHEST,block.getDefaultState());
                player().getInventoryEnderChest().clear();player().inventory.clear();
                net.minecraft.item.Item held=fixture==0?Items.DIAMOND_PICKAXE:fixture==1?Items.DIAMOND_HOE:fixture==2?Items.ELYTRA:Items.DIAMOND;
                player().inventory.setInventorySlotContents(0,new ItemStack(held,fixture<3?1:7));
                player().inventory.currentItem=0;deniedPosition=null;
                if(fixture==3) chest().setLockCode(new net.minecraft.world.LockCode("fixture-key"));
                if(fixture==4 || fixture==10) player().world.setBlockState(CHEST.up(),Blocks.STONE.getDefaultState());
                if(fixture==5 || fixture==7 || fixture==8)
                    for(int slot=0;slot<27;slot++) chest().setInventorySlotContents(slot,new ItemStack(Blocks.STONE,64));
                if(fixture==7 || fixture==8) {
                    player().world.setBlockState(CHEST.east(),Blocks.CHEST.getDefaultState());
                    if(fixture==8) deniedPosition=CHEST.east();
                }
                if(fixture==6) require(!player().world.isBlockLoaded(distant),"Unloaded-target fixture was already loaded");
                player().inventoryContainer.detectAndSendChanges();
            });
            restrictionPhase=1;return false;
        }
        if(restrictionPhase==1) {
            if(mc.currentScreen!=null || mc.player.getDistanceSq(CHEST)>16) return false;
            punch(++request,fixture==6?distant:CHEST);
            restrictionPhase=2;next=ticks+15;return false;
        }
        pending=server(() -> {
            boolean accepted=fixture==7 || fixture==9;
            require(player().getHeldItemMainhand().getCount()==(accepted?0:fixture<3?1:7),"Restriction fixture "+fixture+" changed held count unexpectedly");
            require(player().openContainer==player().inventoryContainer,"Punch restriction fixture opened a GUI");
            if(fixture==7 || fixture==8) {
                require(total(chest(),net.minecraft.item.Item.getItemFromBlock(Blocks.STONE))==27*64,"Double chest first half changed");
                TileEntityChest other=(TileEntityChest)player().world.getTileEntity(CHEST.east());
                require(total(other,Items.DIAMOND)==(accepted?7:0),"Double chest second-half access/capacity failure");
            } else if(fixture==9 || fixture==10) {
                int diamonds=0;
                for(int slot=0;slot<27;slot++) if(player().getInventoryEnderChest().getStackInSlot(slot).getItem()==Items.DIAMOND)
                    diamonds+=player().getInventoryEnderChest().getStackInSlot(slot).getCount();
                require(diamonds==(accepted?7:0),"Ender chest access/count failure");
            } else if(fixture==11) require(((net.minecraft.inventory.IInventory)player().world.getTileEntity(CHEST)).isEmpty(),"Unsupported punch changed shulker contents");
            else if(fixture==5) require(total(chest(),net.minecraft.item.Item.getItemFromBlock(Blocks.STONE))==27*64,"Full chest changed");
            else require(chest().isEmpty(),"Rejected punch changed chest contents in fixture "+fixture);
            if(fixture==6) require(!player().world.isBlockLoaded(distant),"Punch loaded a distant chunk");
            deniedPosition=null;
        });
        pass("Punch restriction fixture "+fixture+" (equipment/locks/capacity/reach/double/ender/unsupported)");
        restrictionCase++;restrictionPhase=0;
        return restrictionCase==12;
    }
    private void dispatchChestRow(int number,boolean keyboard) throws Exception {
        if(keyboard) {
            Field bindings=ClientProxy.class.getDeclaredField("swapRows");bindings.setAccessible(true);
            net.minecraft.client.settings.KeyBinding binding=((net.minecraft.client.settings.KeyBinding[])bindings.get(StorageTweaks.proxy))[number-1];
            binding.setKeyCode(org.lwjgl.input.Keyboard.KEY_G);
            try {
                java.lang.reflect.Method rowKey=ClientProxy.class.getDeclaredMethod("rowKey",net.minecraft.client.gui.GuiScreen.class,int.class);rowKey.setAccessible(true);
                require((boolean)rowKey.invoke(StorageTweaks.proxy,mc.currentScreen,org.lwjgl.input.Keyboard.KEY_G),"Rebound chest row key did not dispatch");
            } finally { binding.setKeyCode(0); }
        } else {
            net.minecraft.client.gui.GuiButton selected=null;
            for(net.minecraft.client.gui.GuiButton button:buttons) if(button.displayString.equals(Integer.toString(number))) selected=button;
            require(selected!=null,"Missing numbered chest row button");
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre(mc.currentScreen,selected,buttons));
        }
        require(ClientProxy.JOB.active(),"Chest row operation did not start");
    }
    private void punch(long id,BlockPos pos) throws Exception {
        mc.player.rotationYaw=0;mc.player.rotationPitch=20;
        mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(0,20,mc.player.onGround));
        ClientProxy proxy=(ClientProxy)StorageTweaks.proxy;
        Field session=ClientProxy.class.getDeclaredField("session"),token=ClientProxy.class.getDeclaredField("token");
        session.setAccessible(true);token.setAccessible(true);
        PunchNetwork.Message message=new PunchNetwork.Message();message.type=3;message.session=session.getLong(proxy);message.token=token.getLong(proxy);
        message.request=id;message.pos=pos;message.slot=mc.player.inventory.currentItem;PunchNetwork.channel.sendToServer(message);
    }
    private Future<?> server(Runnable action) { return mc.getIntegratedServer().addScheduledTask(action); }
    private EntityPlayerMP player() { return mc.getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private TileEntityChest chest() { return (TileEntityChest)player().world.getTileEntity(CHEST); }
    private net.minecraft.inventory.IInventory variantStorage() {
        if(variant==2) return player().getInventoryEnderChest();
        net.minecraft.block.Block block=player().world.getBlockState(CHEST).getBlock();
        if(block instanceof net.minecraft.block.BlockChest) return ((net.minecraft.block.BlockChest)block).getLockableContainer(player().world,CHEST);
        return (net.minecraft.inventory.IInventory)player().world.getTileEntity(CHEST);
    }
    private int total(TileEntityChest chest,net.minecraft.item.Item item) { int total=0;for(int i=0;i<27;i++) if(chest.getStackInSlot(i).getItem()==item) total+=chest.getStackInSlot(i).getCount();return total; }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private void pass(String label) { StorageTweaks.LOG.info("STORAGE_TWEAKS_RUNTIME_PASS: "+label); }
}


