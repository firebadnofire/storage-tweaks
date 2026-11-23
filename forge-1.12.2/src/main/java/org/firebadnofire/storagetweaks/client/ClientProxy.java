package org.firebadnofire.storagetweaks.client;

import io.netty.channel.*;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.*;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.firebadnofire.storagetweaks.*;
import org.firebadnofire.storagetweaks.inventory.*;
import org.firebadnofire.storagetweaks.server.PunchNetwork;
import java.util.*;

public final class ClientProxy extends CommonProxy {
    static final InventoryJob JOB=new InventoryJob();
    static Finder finder;
    static int ticks;
    static boolean observing;
    private NetworkManager connection;
    private KeyBinding settings;
    private KeyBinding cancel;
    private KeyBinding punchOverride;
    private final KeyBinding[] swapRows=new KeyBinding[9];
    private GuiContainer controlsScreen;
    private List<GuiButton> controls=Collections.emptyList();
    private GuiScreen consumedPressScreen;
    private long session,token,request;
    private int lastCapability=-1000, lastPunch=-1000;
    private boolean punchAvailable;
    private int sentPunchPreference=-1;
    private BlockPos authorizedTarget;
    private ItemStack authorizedStack=ItemStack.EMPTY;
    private int authorizedTick=-1000,probeTick=-1000;
    private long probeRequest;
    private ItemStack probeStack=ItemStack.EMPTY;
    private BlockPos probeTarget;
    private boolean probePending;
    private int probeSlot=-1,authorizedSlot=-1;
    private Object authorizedWorld;
    public static String capabilityStatus="unavailable";

    @Override public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        boolean conflict=false;
        for(KeyBinding key:Minecraft.getMinecraft().gameSettings.keyBindings) if(key.getKeyCode()==Keyboard.KEY_P) conflict=true;
        settings=new KeyBinding("key.storage_tweaks.settings",conflict?Keyboard.KEY_NONE:Keyboard.KEY_P,"key.categories.storage_tweaks");
        cancel=new KeyBinding("key.storage_tweaks.cancel",Keyboard.KEY_NONE,"key.categories.storage_tweaks");
        punchOverride=new KeyBinding("key.storage_tweaks.punchOverride",Keyboard.KEY_LSHIFT,"key.categories.storage_tweaks");
        ClientRegistry.registerKeyBinding(punchOverride);
        ClientRegistry.registerKeyBinding(settings); ClientRegistry.registerKeyBinding(cancel);
        for(int row=1;row<=swapRows.length;row++) {
            swapRows[row-1]=new KeyBinding("key.storage_tweaks.swap"+row,Keyboard.KEY_NONE,"key.categories.storage_tweaks");
            ClientRegistry.registerKeyBinding(swapRows[row-1]);
        }
        if(conflict) StorageTweaks.LOG.info("P is already bound; bind Storage Tweaks settings in Controls or use Mods > Config");
        LookCommand.register();
        if(!Loader.isModLoaded("clientcommands")) { finder=new Finder(); finder.register(); }
        else StorageTweaks.LOG.info("clientcommands detected: all Storage Tweaks finders and finder services are disabled");
    }
    public static void say(String text) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player!=null) mc.player.sendMessage(new TextComponentString("[Storage Tweaks] "+text));
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.START) {
            // A held attack can otherwise enter vanilla's repeated/creative break path
            // without another MouseEvent after the selected slot or target changes.
            Minecraft mc=Minecraft.getMinecraft();
            if(candidate()) {
                mc.playerController.resetBlockRemoving();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),false);
                while(mc.gameSettings.keyBindAttack.isPressed()) { }
            }
            return;
        }
        ticks++; Minecraft mc=Minecraft.getMinecraft();
        NetworkManager current=mc.getConnection()==null?null:mc.getConnection().getNetworkManager();
        updateConnection(current);
        syncPunchPreference();
        JOB.tick(ticks);
        if(finder!=null) finder.tick();
        if(ticks-lastCapability>250) { punchAvailable=false; capabilityStatus="unavailable or timed out"; }
        int selected=mc.player==null?-1:mc.player.inventory.currentItem;
        boolean candidate=targetCandidate();
        boolean changed=!candidate || probeSlot!=selected || !Objects.equals(probeTarget,mc.objectMouseOver.getBlockPos()) ||
                !ItemStack.areItemStacksEqual(probeStack,mc.player.getHeldItemMainhand());
        if(changed) {
            if(probePending || !probeStack.isEmpty()) authorizedTarget=null;
            probePending=false;probeStack=ItemStack.EMPTY;
        }
        if(candidate && (changed || ticks-probeTick>=6) && (!probePending || ticks-probeTick>=Settings.timeout)) {
            mc.playerController.updateController(); // Sync vanilla held-slot packet before the probe.
            probeTick=ticks;
            PunchNetwork.Message probe=new PunchNetwork.Message();probe.type=5;probe.session=session;probe.token=token;probe.enabled=true;
            probe.request=++request;probe.slot=mc.player.inventory.currentItem;probe.pos=mc.objectMouseOver.getBlockPos();
            probeSlot=selected;probeRequest=probe.request;probeStack=mc.player.getHeldItemMainhand().copy();
            probeTarget=probe.pos;probePending=true;
            PunchNetwork.channel.sendToServer(probe);
        }
        for(int row=1;row<=swapRows.length;row++) while(swapRows[row-1].isPressed()) {
            if(mc.currentScreen==null) swapRow(row);
        }
        while(cancel.isPressed()) { JOB.stop("Cancelled"); if(finder!=null) finder.cancel("Search cancelled"); }
        while(settings.isPressed()) {
            JOB.stop("Settings opened"); if(finder!=null) finder.cancel("Settings opened");
            mc.displayGuiScreen(ConfigFactory.screen(mc.currentScreen));
        }
    }
    private boolean overrideHeld() {
        if(punchOverride==null || punchOverride.getKeyCode()==Keyboard.KEY_NONE) return false;
        int key=punchOverride.getKeyCode();
        // Read physical state as well: Shift is deliberately shared with vanilla Sneak.
        return punchOverride.getKeyConflictContext().isActive() && punchOverride.getKeyModifier().isActive(punchOverride.getKeyConflictContext()) &&
                (punchOverride.isKeyDown() || (key<0?Mouse.isButtonDown(key+100):Keyboard.isKeyDown(key)));
    }
    private String overrideHint() {
        int key=punchOverride.getKeyCode();
        if((key==Keyboard.KEY_LSHIFT || key==Keyboard.KEY_RSHIFT) && punchOverride.getKeyModifier()==net.minecraftforge.client.settings.KeyModifier.NONE) return "[SHIFT]";
        return "["+(key==Keyboard.KEY_NONE?"UNBOUND":punchOverride.getDisplayName().toUpperCase(Locale.ROOT))+"]";
    }
    private void syncPunchPreference() {
        int preference=(Settings.enabled && Settings.punch?1:0) | (overrideHeld()?2:0);
        if(connection==null || lastCapability<0 || sentPunchPreference==preference) return;
        PunchNetwork.Message message=new PunchNetwork.Message();message.type=7;message.session=session;message.token=token;
        message.enabled=(preference&1)!=0;message.slot=(preference&2)!=0?1:0;PunchNetwork.channel.sendToServer(message);sentPunchPreference=preference;
    }
    private void swapRow(int row) {
        Minecraft mc=Minecraft.getMinecraft();
        if(!Settings.enabled || !Settings.inventorySwap || !observing || mc.player==null || mc.player.isSpectator()) return;
        if(JOB.active() || finder!=null && finder.interacting()) { say("Another inventory operation is active"); return; }
        if(mc.currentScreen!=null || mc.player.openContainer!=mc.player.inventoryContainer) return;
        if(row>3) { say("Open a supported chest to swap row "+row);return; }
        try {
            net.minecraft.inventory.Container container=mc.player.inventoryContainer;
            JOB.start(null,Planner.swapRows(InventoryAdapter.snapshot(container),InventoryAdapter.playerRow(container,9+(3-row)*9),InventoryAdapter.playerRow(container,0)),ticks);
        } catch(IllegalArgumentException | IllegalStateException failure) { say(failure.getMessage());StorageTweaks.LOG.debug("Row swap rejected",failure); }
    }
    private void updateConnection(NetworkManager current) {
        if(current!=connection) {
            JOB.stop("Connection changed"); if(finder!=null) finder.reset();
            observing=false; punchAvailable=false; sentPunchPreference=-1; lastCapability=-1000; session=token=request=0;
            authorizedTarget=null;authorizedStack=ItemStack.EMPTY;authorizedTick=probeTick=-1000;
            probeStack=ItemStack.EMPTY;probeRequest=0;
            probeTarget=null;probePending=false;probeSlot=authorizedSlot=-1;
            capabilityStatus="unavailable"; connection=current;
            if(current!=null) installObserver(current);
        }
    }
    private void installObserver(NetworkManager manager) {
        manager.channel().eventLoop().execute(() -> {
            try {
                manager.channel().pipeline().addBefore("packet_handler","storage_tweaks_observer",new ChannelDuplexHandler() {
                    @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise) throws Exception {
                        if(message instanceof CPacketClickWindow) {
                            CPacketClickWindow packet=(CPacketClickWindow)message;
                            Minecraft.getMinecraft().addScheduledTask(() -> { if(connection==manager) JOB.outgoing(packet); });
                        }
                        super.write(context,message,promise);
                    }
                    @Override public void channelRead(ChannelHandlerContext context,Object message) throws Exception {
                        super.channelRead(context,message);
                        if(!(message instanceof SPacketConfirmTransaction) && !(message instanceof SPacketWindowItems) && !(message instanceof SPacketSetSlot)) return;
                        Minecraft.getMinecraft().addScheduledTask(() -> {
                            if(connection!=manager) return;
                            if(message instanceof SPacketConfirmTransaction) JOB.confirmed((SPacketConfirmTransaction)message,ticks);
                            if(finder!=null && message instanceof SPacketWindowItems) finder.contents((SPacketWindowItems)message);
                            if(message instanceof SPacketWindowItems || message instanceof SPacketSetSlot) JOB.authoritativeUpdate();
                        });
                    }
                });
                Minecraft.getMinecraft().addScheduledTask(() -> { if(connection==manager) observing=true; });
            } catch(RuntimeException failure) {
                StorageTweaks.LOG.error("Cannot observe inventory protocol; inventory automation remains disabled",failure);
                Minecraft.getMinecraft().addScheduledTask(() -> say("Inventory packet observer unavailable; see log"));
            }
        });
    }
    @SubscribeEvent public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        controlsScreen=null;controls=Collections.emptyList();consumedPressScreen=null;
        if(!(event.getGui() instanceof GuiContainer) || !Settings.enabled) return;
        GuiContainer screen=(GuiContainer)event.getGui();
        if(!InventoryAdapter.supported(screen) && !(screen instanceof GuiContainerCreative)) return;
        boolean player=screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
        if(player) {
            if(Settings.inventorySort) event.getButtonList().add(new OperationButton(screen,screen.getGuiLeft()+screen.getXSize()+2,Math.max(0,screen.getGuiTop()),"Sort",2));
            if(Settings.inventorySwap) for(int row=1;row<=3;row++)
                event.getButtonList().add(new OperationButton(screen,screen.getGuiLeft()-20,
                    screen.getGuiTop()+(screen instanceof GuiContainerCreative?54:84)+(3-row)*18,Integer.toString(row),row+2));
            controlsScreen=screen;controls=event.getButtonList();return;
        }
        if(Settings.inventorySwap) for(int row=1;row<=InventoryAdapter.swapRowCount(screen);row++) {
            int[] slots=InventoryAdapter.swapRow(InventoryAdapter.container(screen),row);
            int rowY=InventoryAdapter.container(screen).getSlot(slots[0]).yPos;
            event.getButtonList().add(new OperationButton(screen,screen.getGuiLeft()-20,screen.getGuiTop()+rowY,Integer.toString(row),row+2));
        }
        int x=screen.getGuiLeft(), y=screen.getGuiTop()-21;
        boolean side=y<0;
        if(side) { x=screen.getGuiLeft()-70;y=Math.max(0,screen.getGuiTop()); }
        if(Settings.chestButtons) {
            event.getButtonList().add(new OperationButton(screen,side?x:x+10,y,"Take",0));
            event.getButtonList().add(new OperationButton(screen,side?x:x+64,side?y+22:y,"Store",1));
            event.getButtonList().add(new OperationButton(screen,side?x:x+118,side?y+44:y,"Sort",2));
        }
        controlsScreen=screen;controls=event.getButtonList();
    }
    private static final class OperationButton extends GuiButton {
        final int operation;
        final GuiContainer owner;
        OperationButton(GuiContainer owner,int x,int y,String label,int operation) {
            super(0,x,y,operation>=3?18:48,operation>=3?18:20,label);this.owner=owner;this.operation=operation;updateVisibility();
        }
        private void updateVisibility() {
            if(owner instanceof GuiContainerCreative) visible=Settings.enabled && (operation>=3?Settings.inventorySwap:Settings.inventorySort) && InventoryAdapter.creative(owner);
        }
        @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks) {
            updateVisibility();super.drawButton(mc,mouseX,mouseY,partialTicks);
        }
        @Override public boolean mousePressed(Minecraft mc,int mouseX,int mouseY) {
            updateVisibility();return super.mousePressed(mc,mouseX,mouseY);
        }
    }
    @SubscribeEvent public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if(!(event.getButton() instanceof OperationButton)) return;
        event.setCanceled(true);
        if(!Settings.enabled || !observing) { say("Inventory automation is unavailable until protocol observation is ready"); return; }
        if(JOB.active() || finder!=null && finder.interacting()) { say("Another inventory operation is active"); return; }
        GuiContainer screen=(GuiContainer)event.getGui();
        if(!InventoryAdapter.supported(screen) || Minecraft.getMinecraft().player.isSpectator()) return;
        try {
            int operation=((OperationButton)event.getButton()).operation;
            Snapshot snapshot=InventoryAdapter.snapshot(InventoryAdapter.container(screen));
            int[] storage=InventoryAdapter.group(InventoryAdapter.container(screen),false,false);
            int[] player=InventoryAdapter.group(InventoryAdapter.container(screen),true,true);
            Plan plan;
            if(operation>=3) {
                if(!Settings.inventorySwap || operation-2>InventoryAdapter.swapRowCount(screen)) return;
                plan=Planner.swapRows(snapshot,InventoryAdapter.swapRow(InventoryAdapter.container(screen),operation-2),InventoryAdapter.playerRow(InventoryAdapter.container(screen),0));
            }
            else if(operation==0) plan=Planner.transfer(snapshot,storage,player);
            else if(operation==1) {
                int[] eligible=Arrays.stream(player).filter(slot -> !Arrays.asList(Settings.storeExclusions).contains(snapshot.slots.get(slot).registryId)).toArray();
                plan=Planner.transfer(snapshot,eligible,storage);
            } else plan=Planner.sort(snapshot,(screen instanceof GuiInventory || InventoryAdapter.creative(screen))?InventoryAdapter.group(InventoryAdapter.container(screen),true,false):storage,Settings.caseSensitive);
            JOB.start(screen,plan,ticks);
        } catch(IllegalArgumentException | IllegalStateException failure) { say(failure.getMessage()); StorageTweaks.LOG.debug("Inventory plan rejected",failure); }
    }
    @SubscribeEvent public void mouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if(Mouse.getEventButton()==0) {
            if(!Mouse.getEventButtonState() && consumedPressScreen==event.getGui()) {
                consumedPressScreen=null;event.setCanceled(true);return;
            }
            if(Mouse.getEventButtonState() && controlsScreen==event.getGui()) {
                Minecraft mc=Minecraft.getMinecraft();
                int x=Mouse.getEventX()*controlsScreen.width/mc.displayWidth;
                int y=controlsScreen.height-Mouse.getEventY()*controlsScreen.height/mc.displayHeight-1;
                for(GuiButton button:controls) if(button instanceof OperationButton && button.mousePressed(mc,x,y)) {
                    // GuiContainer continues after GuiScreen's button callback and treats our
                    // outside-frame controls as slot -999. Consume BOTH edges before that path.
                    consumedPressScreen=event.getGui();event.setCanceled(true);
                    button.playPressSound(mc.getSoundHandler());
                    MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Pre(event.getGui(),button,controls));
                    return;
                }
            }
        }
        if(JOB.active() && (Mouse.getEventButtonState() || Mouse.getEventDWheel()!=0)) { JOB.stop("Manual inventory interaction"); event.setCanceled(true); }
    }
    private boolean rowKey(GuiScreen screen,int key) {
        if(!Settings.inventorySwap || controlsScreen!=screen) return false;
        for(int row=1;row<=swapRows.length;row++) if(swapRows[row-1].getKeyCode()!=Keyboard.KEY_NONE && swapRows[row-1].isActiveAndMatches(key)) {
            for(GuiButton button:controls) if(button instanceof OperationButton && ((OperationButton)button).operation==row+2) {
                OperationButton operation=(OperationButton)button;operation.updateVisibility();
                if(operation.visible) {
                    MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Pre(screen,button,controls));return true;
                }
            }
        }
        return false;
    }
    @SubscribeEvent public void keyInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if(Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent() && rowKey(event.getGui(),Keyboard.getEventKey())) {
            event.setCanceled(true);return;
        }
        if(JOB.active() && Keyboard.getEventKeyState()) {
            JOB.stop("Manual keyboard interaction");
            // Let Escape close normally, preserving vanilla cursor handling.
            if(Keyboard.getEventKey()!=Keyboard.KEY_ESCAPE) event.setCanceled(true);
        }
    }
    @SubscribeEvent public void worldKey(InputEvent.KeyInputEvent event) {
        syncPunchPreference();
        Minecraft mc=Minecraft.getMinecraft();
        if(Keyboard.getEventKeyState() && mc.gameSettings.keyBindAttack.isActiveAndMatches(Keyboard.getEventKey()) && candidate()) {
            sendPunch();return;
        }
        if(JOB.active() && Keyboard.getEventKeyState()) JOB.stop("Manual keyboard interaction");
        if(finder!=null && finder.interacting() && Keyboard.getEventKeyState() && Keyboard.getEventKey()==Keyboard.KEY_ESCAPE) finder.cancel("Search cancelled");
    }
    @SubscribeEvent public void open(GuiOpenEvent event) { if(finder!=null) finder.open(event); }
    @SubscribeEvent public void mouse(MouseEvent event) {
        syncPunchPreference();
        if(JOB.active() && (event.isButtonstate() || event.getDwheel()!=0)) { JOB.stop("Manual inventory interaction");event.setCanceled(true);return; }
        if(finder!=null && finder.interacting() && event.getButton()>=0 && event.isButtonstate()) { finder.cancel("Manual interaction cancelled the item search"); event.setCanceled(true); return; }
        if(event.getButton()!=0 || !event.isButtonstate() || !candidate()) return;
        event.setCanceled(true);
        sendPunch();
    }
    @SubscribeEvent public void attackBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        // Stop local prediction and cover remapped attack paths. The negotiated
        // server also guards START_DESTROY, which creative sends before this event.
        if(event.getWorld().isRemote && candidate() && event.getPos().equals(Minecraft.getMinecraft().objectMouseOver.getBlockPos())) {
            event.setCanceled(true);sendPunch();
        }
    }
    private void sendPunch() {
        Minecraft mc=Minecraft.getMinecraft();
        mc.playerController.resetBlockRemoving();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),false);
        while(mc.gameSettings.keyBindAttack.isPressed()) { }
        if(ticks==lastPunch) return;
        lastPunch=ticks;
        authorizedTarget=null;
        mc.playerController.updateController(); // Ordered CPacketHeldItemChange, then deposit on the same connection.
        PunchNetwork.Message message=new PunchNetwork.Message(); message.type=3; message.session=session; message.token=token;
        message.enabled=overrideHeld();
        message.request=++request; message.slot=mc.player.inventory.currentItem; message.pos=mc.objectMouseOver.getBlockPos();
        PunchNetwork.channel.sendToServer(message);
    }
    private boolean candidate() {
        Minecraft mc=Minecraft.getMinecraft();
        return targetCandidate() && PunchNetwork.eligible(mc.player.getHeldItemMainhand(),overrideHeld());
    }
    private boolean targetCandidate() {
        Minecraft mc=Minecraft.getMinecraft();
        if(!Settings.enabled || !Settings.punch || !punchAvailable || mc.player==null || mc.currentScreen!=null ||
                mc.player.isSpectator() || mc.player.getHeldItemMainhand().isEmpty() || mc.objectMouseOver==null ||
                mc.objectMouseOver.typeOfHit!=RayTraceResult.Type.BLOCK) return false;
        Block block=mc.world.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
        return (block.getClass()==BlockChest.class || block.getClass()==BlockEnderChest.class) &&
                mc.player.getPositionEyes(1).distanceTo(mc.objectMouseOver.hitVec)<=mc.playerController.getBlockReachDistance();
    }
    private boolean available() {
        Minecraft mc=Minecraft.getMinecraft();
        return targetCandidate() && authorizedTarget!=null && authorizedTarget.equals(mc.objectMouseOver.getBlockPos()) &&
                mc.world==authorizedWorld && authorizedSlot==mc.player.inventory.currentItem && ticks-authorizedTick<=10 && ItemStack.areItemStacksEqual(authorizedStack,mc.player.getHeldItemMainhand());
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Text event) {
        if(Settings.hover && available()) {
            Minecraft mc=Minecraft.getMinecraft(); String label="PUNCH TO DEPOSIT";
            mc.fontRenderer.drawStringWithShadow(label,(event.getResolution().getScaledWidth()-mc.fontRenderer.getStringWidth(label))/2f,
                    event.getResolution().getScaledHeight()/2f+18,0xffffff);
            if(PunchNetwork.requiresOverride(mc.player.getHeldItemMainhand())) {
                String hint=overrideHint();
                mc.fontRenderer.drawStringWithShadow(hint,(event.getResolution().getScaledWidth()-mc.fontRenderer.getStringWidth(hint))/2f,
                        event.getResolution().getScaledHeight()/2f+30,0xffffff);
            }
        }
    }
    @Override public void capabilities(PunchNetwork.Message message) {
        Minecraft mc=Minecraft.getMinecraft();
        NetworkManager source=mc.getConnection()==null?null:mc.getConnection().getNetworkManager();
        mc.addScheduledTask(() -> {
            if(source==null || mc.getConnection()==null || mc.getConnection().getNetworkManager()!=source || message.version!=PunchNetwork.VERSION) return;
            updateConnection(source);
            if(message.type==0) {
                session=message.session; token=message.token; punchAvailable=false;
                PunchNetwork.Message reply=new PunchNetwork.Message(); reply.type=1; reply.session=session; reply.token=token;
                reply.enabled=Settings.enabled && Settings.punch;reply.slot=overrideHeld()?1:0;
                sentPunchPreference=(reply.enabled?1:0) | (reply.slot==1?2:0);
                PunchNetwork.channel.sendToServer(reply);
            } else if(message.type==2 && message.session==session) {
                if(token!=message.token) { probePending=false;authorizedTarget=null; }
                token=message.token; punchAvailable=message.enabled; lastCapability=ticks;
                capabilityStatus=message.enabled?"available":"disabled by server policy or unreviewed protection integration";
            } else if(message.type==6 && message.session==session && message.token==token && message.request==probeRequest) {
                probePending=false;
                authorizedTarget=null;
                if(message.enabled && targetCandidate() && message.pos.equals(mc.objectMouseOver.getBlockPos()) && message.slot==mc.player.inventory.currentItem &&
                        ItemStack.areItemStacksEqual(probeStack,mc.player.getHeldItemMainhand())) {
                    authorizedSlot=message.slot;authorizedTarget=message.pos;authorizedTick=ticks;authorizedWorld=mc.world;authorizedStack=mc.player.getHeldItemMainhand().copy();
                }
            }
        });
    }
}
