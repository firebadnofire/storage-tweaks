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
            if(mode.equals("creative-regression") && stage==0) stage=50;
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
                    if(!mode.equals("pack")) { stage=50;break; }
                    pass(mode.equals("pack")?"ALL PACK RUNTIME TESTS PASSED":"ALL INTEGRATED RUNTIME TESTS PASSED");stage++;mc.shutdown();break;
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


