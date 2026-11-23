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
    private GuiContainer controlsScreen;
    private List<GuiButton> controls=Collections.emptyList();
    private GuiScreen consumedPressScreen;
    private long session,token,request;
    private int lastCapability=-1000, lastPunch=-1000;
    private boolean punchAvailable;
    private BlockPos authorizedTarget;
    private ItemStack authorizedStack=ItemStack.EMPTY;
    private int authorizedTick=-1000,probeTick=-1000;
    private long probeRequest;
    private ItemStack probeStack=ItemStack.EMPTY;
    private BlockPos probeTarget;
    private boolean probePending;
    private Object authorizedWorld;
    public static String capabilityStatus="unavailable";

    @Override public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        boolean conflict=false;
        for(KeyBinding key:Minecraft.getMinecraft().gameSettings.keyBindings) if(key.getKeyCode()==Keyboard.KEY_P) conflict=true;
        settings=new KeyBinding("key.storage_tweaks.settings",conflict?Keyboard.KEY_NONE:Keyboard.KEY_P,"key.categories.storage_tweaks");
        cancel=new KeyBinding("key.storage_tweaks.cancel",Keyboard.KEY_NONE,"key.categories.storage_tweaks");
        ClientRegistry.registerKeyBinding(settings); ClientRegistry.registerKeyBinding(cancel);
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
        if(event.phase!=TickEvent.Phase.END) return;
        ticks++; Minecraft mc=Minecraft.getMinecraft();
        NetworkManager current=mc.getConnection()==null?null:mc.getConnection().getNetworkManager();
        updateConnection(current);
        JOB.tick(ticks);
        if(finder!=null) finder.tick();
        if(ticks-lastCapability>250) { punchAvailable=false; capabilityStatus="unavailable or timed out"; }
        boolean candidate=candidate();
        if(!candidate || !Objects.equals(probeTarget,mc.objectMouseOver.getBlockPos()) ||
                !ItemStack.areItemStacksEqual(probeStack,mc.player.getHeldItemMainhand())) {
            if(probePending || !probeStack.isEmpty()) { authorizedTarget=null;probeTick=ticks-6; }
            probePending=false;probeStack=ItemStack.EMPTY;
        }
        if(candidate && ticks-probeTick>=6 && (!probePending || ticks-probeTick>=Settings.timeout)) {
            probeTick=ticks;
            PunchNetwork.Message probe=new PunchNetwork.Message();probe.type=5;probe.session=session;probe.token=token;
            probe.request=++request;probe.slot=mc.player.inventory.currentItem;probe.pos=mc.objectMouseOver.getBlockPos();
            probeRequest=probe.request;probeStack=mc.player.getHeldItemMainhand().copy();
            probeTarget=probe.pos;probePending=true;
            PunchNetwork.channel.sendToServer(probe);
        }
        while(cancel.isPressed()) { JOB.stop("Cancelled"); if(finder!=null) finder.cancel("Search cancelled"); }
        while(settings.isPressed()) {
            JOB.stop("Settings opened"); if(finder!=null) finder.cancel("Settings opened");
            mc.displayGuiScreen(ConfigFactory.screen(mc.currentScreen));
        }
    }
    private void updateConnection(NetworkManager current) {
        if(current!=connection) {
            JOB.stop("Connection changed"); if(finder!=null) finder.reset();
            observing=false; punchAvailable=false; lastCapability=-1000; session=token=request=0;
            authorizedTarget=null;authorizedStack=ItemStack.EMPTY;authorizedTick=probeTick=-1000;
            probeStack=ItemStack.EMPTY;probeRequest=0;
            probeTarget=null;probePending=false;
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
        if(player?!Settings.inventorySort:!Settings.chestButtons) return;
        int x=screen.getGuiLeft(), y=screen.getGuiTop()-21;
        if(screen instanceof GuiContainerCreative) {
            OperationButton button=new OperationButton(screen,screen.getGuiLeft()+screen.getXSize()+2,Math.max(0,screen.getGuiTop()),"Sort",2);
            button.visible=InventoryAdapter.creative(screen);
            event.getButtonList().add(button);
            controlsScreen=screen;controls=event.getButtonList();return;
        }
        boolean side=y<0;
        if(side) { x-=50;y=Math.max(0,screen.getGuiTop());if(x<0) return; }
        if(player) event.getButtonList().add(new OperationButton(screen,side?x:x+120,y,"Sort",2));
        else {
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
            super(0,x,y,48,20,label);this.owner=owner;this.operation=operation;
        }
        private void updateVisibility() {
            if(owner instanceof GuiContainerCreative) visible=Settings.enabled && Settings.inventorySort && InventoryAdapter.creative(owner);
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
            if(operation==0) plan=Planner.transfer(snapshot,storage,player);
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
    @SubscribeEvent public void keyInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if(JOB.active() && Keyboard.getEventKeyState()) {
            JOB.stop("Manual keyboard interaction");
            // Let Escape close normally, preserving vanilla cursor handling.
            if(Keyboard.getEventKey()!=Keyboard.KEY_ESCAPE) event.setCanceled(true);
        }
    }
    @SubscribeEvent public void worldKey(InputEvent.KeyInputEvent event) {
        if(finder!=null && finder.interacting() && Keyboard.getEventKeyState() && Keyboard.getEventKey()==Keyboard.KEY_ESCAPE) finder.cancel("Search cancelled");
    }
    @SubscribeEvent public void open(GuiOpenEvent event) { if(finder!=null) finder.open(event); }
    @SubscribeEvent public void mouse(MouseEvent event) {
        if(finder!=null && finder.interacting() && event.getButton()>=0 && event.isButtonstate()) { finder.cancel("Manual interaction cancelled the item search"); event.setCanceled(true); return; }
        if(event.getButton()!=0 || !event.isButtonstate() || !available()) return;
        Minecraft mc=Minecraft.getMinecraft();
        event.setCanceled(true);
        if(ticks-lastPunch<4) return;
        lastPunch=ticks;
        authorizedTarget=null;
        PunchNetwork.Message message=new PunchNetwork.Message(); message.type=3; message.session=session; message.token=token;
        message.request=++request; message.slot=mc.player.inventory.currentItem; message.pos=mc.objectMouseOver.getBlockPos();
        PunchNetwork.channel.sendToServer(message);
    }
    private boolean candidate() {
        Minecraft mc=Minecraft.getMinecraft();
        if(!Settings.enabled || !Settings.punch || !punchAvailable || mc.player==null || mc.currentScreen!=null ||
                mc.player.isSpectator() || !PunchNetwork.eligible(mc.player.getHeldItemMainhand()) || mc.objectMouseOver==null ||
                mc.objectMouseOver.typeOfHit!=RayTraceResult.Type.BLOCK) return false;
        Block block=mc.world.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
        return (block.getClass()==BlockChest.class || block.getClass()==BlockEnderChest.class) &&
                mc.player.getPositionEyes(1).distanceTo(mc.objectMouseOver.hitVec)<=mc.playerController.getBlockReachDistance();
    }
    private boolean available() {
        Minecraft mc=Minecraft.getMinecraft();
        return candidate() && authorizedTarget!=null && authorizedTarget.equals(mc.objectMouseOver.getBlockPos()) &&
                mc.world==authorizedWorld && ticks-authorizedTick<=10 && ItemStack.areItemStacksEqual(authorizedStack,mc.player.getHeldItemMainhand());
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Text event) {
        if(Settings.hover && available()) {
            Minecraft mc=Minecraft.getMinecraft(); String label="PUNCH TO DEPOSIT";
            mc.fontRenderer.drawStringWithShadow(label,(event.getResolution().getScaledWidth()-mc.fontRenderer.getStringWidth(label))/2f,
                    event.getResolution().getScaledHeight()/2f+18,0xffffff);
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
                PunchNetwork.channel.sendToServer(reply);
            } else if(message.type==2 && message.session==session) {
                token=message.token; punchAvailable=message.enabled; lastCapability=ticks;
                capabilityStatus=message.enabled?"available":"disabled by server policy or unreviewed protection integration";
            } else if(message.type==6 && message.session==session && message.token==token && message.request==probeRequest) {
                probePending=false;
                authorizedTarget=null;
                if(message.enabled && candidate() && message.pos.equals(mc.objectMouseOver.getBlockPos()) && message.slot==mc.player.inventory.currentItem &&
                        ItemStack.areItemStacksEqual(probeStack,mc.player.getHeldItemMainhand())) {
                    authorizedTarget=message.pos;authorizedTick=ticks;authorizedWorld=mc.world;authorizedStack=mc.player.getHeldItemMainhand().copy();
                }
            }
        });
    }
}
