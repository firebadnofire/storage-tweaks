package org.firebadnofire.storagetweaks.client;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.firebadnofire.storagetweaks.*;
import java.util.*;
import java.util.function.Predicate;

/** Independent implementation; no runtime linkage to clientcommands. */
final class Finder {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Search> searches=new LinkedHashMap<>();
    private Object world;
    private ItemSearch itemSearch;
    private interface Search { boolean tick(); }

    void register() {
        for(String name:Arrays.asList("cfind","cfindblock","cfinditem")) {
            if(ClientCommandHandler.instance.getCommands().containsKey(name)) {
                StorageTweaks.LOG.warn("Not registering /{}: another mod owns this command",name); continue;
            }
            ClientCommandHandler.instance.registerCommand(new CommandBase() {
                @Override public String getName() { return name; }
                @Override public String getUsage(ICommandSender sender) { return "/"+name+" <"+(name.equals("cfind")?"selector":name.equals("cfindblock")?"block":"item")+"> [--keep-searching] | cancel"; }
                @Override public int getRequiredPermissionLevel() { return 0; }
                @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) { return true; }
                @Override public void execute(MinecraftServer server,ICommandSender sender,String[] arguments) throws CommandException {
                    if(arguments.length==1 && arguments[0].equals("cancel")) { cancel(name,"Search cancelled"); return; }
                    if(!Settings.enabled || !Settings.finder) throw new CommandException("Storage Tweaks finder is disabled in settings");
                    if(mc.world==null || mc.player==null) throw new CommandException("Join a world before searching");
                    boolean keep=false; List<String> args=new ArrayList<>();
                    for(String argument:arguments) {
                        if(argument.equals("--keep-searching")) keep=true;
                        else if(argument.startsWith("--")) throw new CommandException("Unsupported flag: "+argument);
                        else args.add(argument);
                    }
                    if(args.isEmpty()) throw new WrongUsageException(getUsage(sender));
                    Search search;
                    if(name.equals("cfind")) {
                        if(args.size()!=1) throw new WrongUsageException(getUsage(sender));
                        search=new EntitySearch(new EntityFilter(args.get(0)),keep);
                    } else if(name.equals("cfindblock")) {
                        if(args.size()>2) throw new WrongUsageException(getUsage(sender));
                        search=new BlockSearch(blockPredicate(args),keep);
                    } else {
                        if(args.size()>2) throw new WrongUsageException(getUsage(sender));
                        Predicate<ItemStack> predicate=itemPredicate(args);
                        if(!ClientProxy.observing || ClientProxy.JOB.active() || interacting() || mc.currentScreen!=null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat) ||
                                mc.player.openContainer!=mc.player.inventoryContainer || !mc.player.inventory.getItemStack().isEmpty())
                            throw new CommandException("Close inventories and finish other inventory operations before /cfinditem");
                        itemSearch=new ItemSearch(predicate,keep); search=itemSearch;
                    }
                    cancel(name,null); searches.put(name,search); world=mc.world;
                    ITextComponent notice=new TextComponentString("[Storage Tweaks] Searching client-visible data. [Cancel]");
                    notice.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/"+name+" cancel"));
                    mc.player.sendMessage(notice);
                }
            });
        }
    }
    boolean interacting() { return itemSearch!=null; }
    void tick() {
        if(mc.world==null || mc.world!=world || mc.player==null || mc.player.isDead || !Settings.enabled || !Settings.finder) { reset(); return; }
        Iterator<Map.Entry<String,Search>> iterator=searches.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String,Search> entry=iterator.next();
            try { if(entry.getValue().tick()) { iterator.remove(); if(entry.getValue()==itemSearch) itemSearch=null; } }
            catch(RuntimeException failure) {
                StorageTweaks.LOG.error("Finder failed: "+entry.getKey(),failure);
                ClientProxy.say("Search failed: "+failure.getMessage()+"; see log");
                if(entry.getValue() instanceof ItemSearch) ((ItemSearch)entry.getValue()).release(true);
                iterator.remove(); if(entry.getValue()==itemSearch) itemSearch=null;
            }
        }
    }
    void reset() { cancel(null); world=mc.world; }
    void cancel(String reason) { for(String name:new ArrayList<>(searches.keySet())) cancel(name,reason); }
    private void cancel(String name,String reason) {
        Search search=searches.remove(name);
        if(search instanceof ItemSearch) { ((ItemSearch)search).release(true); if(itemSearch==search) itemSearch=null; }
        if(search!=null && reason!=null) ClientProxy.say(reason);
    }
    void open(GuiOpenEvent event) {
        if(itemSearch==null) return;
        ItemSearch search=itemSearch;
        GuiScreen screen=event.getGui();
        if(search.waiting && search.screen==null && screen instanceof GuiContainer && InventoryAdapter.supported((GuiContainer)screen) &&
                !(screen instanceof net.minecraft.client.gui.inventory.GuiInventory)) {
            search.screen=(GuiContainer)screen;
            // initGui normally installs this container. Suppression skips initGui, so retain the REAL object here.
            mc.player.openContainer=search.screen.inventorySlots;
            event.setCanceled(true);
        } else if(screen!=null) cancel("Unexpected screen interrupted the item search");
    }
    void contents(SPacketWindowItems packet) {
        if(itemSearch!=null && itemSearch.screen!=null && packet.getWindowId()==itemSearch.screen.inventorySlots.windowId)
            itemSearch.ready=true;
    }
    private void report(String text,BlockPos pos) {
        report(text,pos,Math.sqrt(pos.distanceSqToCenter(mc.player.posX,mc.player.posY,mc.player.posZ)));
    }
    private void report(String text,BlockPos pos,double distance) {
        String coords=pos.getX()+" "+pos.getY()+" "+pos.getZ();
        ITextComponent message=new TextComponentString("[Storage Tweaks] "+text+" ");
        ITextComponent coordinates=new TextComponentString("["+coords+"]");
        coordinates.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/tp "+coords));
        message.appendSibling(coordinates);
        ITextComponent look=new TextComponentString(" [Look at]");
        look.getStyle().setColor(TextFormatting.AQUA).setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/clook "+coords))
                .setHoverEvent(new net.minecraft.util.text.event.HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString("Look at "+coords+" (open chat to click)")));
        message.appendSibling(look);
        message.appendSibling(new TextComponentString(" "+String.format(Locale.ROOT,"%.2f",distance)+"m"));
        mc.player.sendMessage(message);
    }
    private static ResourceLocation id(String text) throws CommandException {
        try { return new ResourceLocation(text); } catch(IllegalArgumentException failure) { throw new CommandException("Invalid registry identifier: "+text); }
    }
    private Predicate<IBlockState> blockPredicate(List<String> args) throws CommandException {
        String input=args.get(0), properties=null;
        int bracket=input.indexOf('[');
        if(bracket>=0) {
            if(!input.endsWith("]") || args.size()!=1) throw new CommandException("Use block[property=value] OR block metadata");
            properties=input.substring(bracket+1,input.length()-1); input=input.substring(0,bracket);
        }
        Block block=Block.REGISTRY.getObject(id(input));
        if(block==null || !Block.REGISTRY.containsKey(id(input))) throw new CommandException("Unknown block: "+input);
        com.google.common.base.Predicate<IBlockState> legacy=CommandBase.convertArgToBlockStatePredicate(block,args.size()==2?args.get(1):"*");
        Map<IProperty<?>,String> filters=new HashMap<>();
        if(properties!=null && !properties.isEmpty()) for(String pair:properties.split(",")) {
            String[] parts=pair.split("=",-1);
            if(parts.length!=2) throw new CommandException("Expected property=value");
            IProperty<?> property=block.getBlockState().getProperty(parts[0]);
            if(property==null || !property.parseValue(parts[1]).isPresent()) throw new CommandException("Unknown property/value: "+pair);
            if(filters.put(property,parts[1])!=null) throw new CommandException("Duplicate property: "+parts[0]);
        }
        return state -> {
            if(state.getBlock()!=block || !legacy.apply(state)) return false;
            for(Map.Entry<IProperty<?>,String> filter:filters.entrySet())
                if(!propertyName(state,filter.getKey()).equals(filter.getValue())) return false;
            return true;
        };
    }
    private static <T extends Comparable<T>> String propertyName(IBlockState state,IProperty<T> property) { return property.getName(state.getValue(property)); }
    private Predicate<ItemStack> itemPredicate(List<String> args) throws CommandException {
        ResourceLocation registry=id(args.get(0)); Item item=Item.REGISTRY.getObject(registry);
        if(item==null) throw new CommandException("Unknown item: "+args.get(0));
        int metadata=args.size()==2?CommandBase.parseInt(args.get(1),0): -1;
        return stack -> !stack.isEmpty() && stack.getItem()==item && (metadata<0 || stack.getMetadata()==metadata);
    }

    private final class EntitySearch implements Search {
        final EntityFilter filter; final boolean keep;
        final Set<UUID> found=new HashSet<>();
        List<Entity> batch; int index, next;
        final List<Entity> matches=new ArrayList<>();
        EntitySearch(EntityFilter filter,boolean keep) { this.filter=filter; this.keep=keep; }
        @Override public boolean tick() {
            if(ClientProxy.ticks<next) return false;
            if(batch==null) { batch=new ArrayList<>(mc.world.loadedEntityList); index=0; matches.clear(); }
            long deadline=System.nanoTime()+Settings.searchMillis*1_000_000L; int worked=0;
            while(index<batch.size() && worked++<Settings.searchBlocks && System.nanoTime()<deadline) {
                Entity entity=batch.get(index++); if(filter.test(entity)) matches.add(entity);
            }
            if(index<batch.size()) return false;
            matches.sort(Comparator.comparingDouble((Entity entity)->entity.getDistanceSq(mc.player)).thenComparing(Entity::getUniqueID));
            if(filter.count<0) Collections.reverse(matches);
            if(filter.kind.equals("@r")) Collections.shuffle(matches);
            int count=Math.min(Settings.results,filter.count==0?Settings.results:Math.abs(filter.count));
            int shown=0;
            for(Entity entity:matches) {
                if(shown>=count) break;
                if(found.add(entity.getUniqueID())) {
                    report(entity.getName()+" ("+(entity instanceof EntityPlayer?"minecraft:player":EntityList.getKey(entity))+")",
                            new BlockPos(entity.posX,entity.posY,entity.posZ),Math.sqrt(entity.getDistanceSq(mc.player))); shown++;
                }
            }
            if(!keep) { ClientProxy.say(matches.isEmpty()?"No matching client-known entities":"Entity search complete; "+shown+" shown (display limit "+count+")"); return true; }
            if(found.size()>4096) found.clear();
            batch=null; next=ClientProxy.ticks+20; return false;
        }
    }
    private final class EntityFilter {
        final String kind; final Map<String,String> options=new HashMap<>(); final int count;
        final double x,y,z,r,rm; final Double dx,dy,dz;
        EntityFilter(String text) throws CommandException {
            String base=text; int bracket=text.indexOf('[');
            if(bracket>=0) {
                if(!text.endsWith("]")) throw new CommandException("Malformed selector");
                base=text.substring(0,bracket); String body=text.substring(bracket+1,text.length()-1);
                if(!body.isEmpty()) for(String pair:body.split(",",-1)) {
                    String[] parts=pair.split("=",2);
                    if(parts.length!=2 || !Arrays.asList("type","name","r","rm","c","x","y","z","dx","dy","dz","tag").contains(parts[0]) || options.put(parts[0],parts[1])!=null)
                        throw new CommandException("Unsupported or duplicate selector filter: "+pair);
                }
            }
            kind=base;
            if(base.startsWith("@") && !Arrays.asList("@e","@a","@p","@r","@s").contains(base)) throw new CommandException("Unsupported selector: "+base);
            if(!base.startsWith("@") && bracket>=0) throw new CommandException("Filters require a selector");
            x=number("x",mc.player.posX); y=number("y",mc.player.posY); z=number("z",mc.player.posZ);
            r=number("r",Double.MAX_VALUE); rm=number("rm",0);
            dx=optional("dx"); dy=optional("dy"); dz=optional("dz");
            if(r<0 || rm<0 || rm>r) throw new CommandException("Invalid selector radius");
            count=options.containsKey("c")?CommandBase.parseInt(options.get("c"),-1000000,1000000):(base.equals("@p")||base.equals("@r")||base.equals("@s")?1:0);
        }
        double number(String key,double fallback) throws CommandException {
            return options.containsKey(key)?CommandBase.parseDouble(options.get(key)):fallback;
        }
        Double optional(String key) throws CommandException { return options.containsKey(key)?number(key,0):null; }
        boolean test(Entity entity) {
            if(kind.equals("@s") && entity!=mc.player) return false;
            if((kind.equals("@a")||kind.equals("@p")||kind.equals("@r")) && !(entity instanceof EntityPlayer)) return false;
            if(!kind.startsWith("@") && !entity.getName().equals(kind) && !entity.getUniqueID().toString().equals(kind)) return false;
            if(!option("name",entity.getName())) return false;
            ResourceLocation type=entity instanceof EntityPlayer?new ResourceLocation("minecraft:player"):EntityList.getKey(entity);
            if(options.containsKey("type")) {
                String wanted=options.get("type"), expected=wanted.startsWith("!")?wanted.substring(1):wanted;
                if(!expected.contains(":")) expected="minecraft:"+expected;
                if((type!=null && type.toString().equals(expected))==wanted.startsWith("!")) return false;
            }
            if(options.containsKey("tag")) {
                String wanted=options.get("tag"); boolean invert=wanted.startsWith("!"); String tag=invert?wanted.substring(1):wanted;
                boolean has=tag.isEmpty()?entity.getTags().isEmpty():entity.getTags().contains(tag);
                if(has==invert) return false;
            }
            double distance=entity.getDistanceSq(x,y,z);
            if(distance>r*r || distance<rm*rm) return false;
            if(dx!=null||dy!=null||dz!=null) {
                double ax=dx==null?0:dx, ay=dy==null?0:dy, az=dz==null?0:dz;
                AxisAlignedBB box=new AxisAlignedBB(x+Math.min(0,ax),y+Math.min(0,ay),z+Math.min(0,az),x+Math.max(0,ax)+1,y+Math.max(0,ay)+1,z+Math.max(0,az)+1);
                if(!entity.getEntityBoundingBox().intersects(box)) return false;
            }
            return true;
        }
        boolean option(String key,String actual) {
            String expected=options.get(key); if(expected==null) return true;
            return expected.startsWith("!")?!actual.equals(expected.substring(1)):actual.equals(expected);
        }
    }
    private final class BlockSearch implements Search {
        final Predicate<IBlockState> predicate; final boolean keep;
        final Set<BlockPos> reported=new HashSet<>();
        BlockPos origin; int radius, x,y,z,next,seen,unloaded;
        final PriorityQueue<BlockPos> nearest=new PriorityQueue<>((a,b)->Double.compare(b.distanceSq(origin),a.distanceSq(origin)));
        BlockSearch(Predicate<IBlockState> predicate,boolean keep) { this.predicate=predicate; this.keep=keep; begin(); }
        void begin() {
            origin=mc.player.getPosition(); radius=Math.min(Settings.searchRadius,mc.gameSettings.renderDistanceChunks*16);
            x=origin.getX()-radius; y=Math.max(0,origin.getY()-radius); z=origin.getZ()-radius;
            seen=unloaded=0; nearest.clear();
        }
        @Override public boolean tick() {
            if(ClientProxy.ticks<next) return false;
            long deadline=System.nanoTime()+Settings.searchMillis*1_000_000L; int work=0;
            while(x<=origin.getX()+radius && work++<Settings.searchBlocks && System.nanoTime()<deadline) {
                BlockPos pos=new BlockPos(x,y,z);
                if(pos.distanceSq(origin)<=radius*radius) {
                    if(mc.world.isBlockLoaded(pos,false)) {
                        seen++;
                        if(predicate.test(mc.world.getBlockState(pos))) {
                            nearest.add(pos); if(nearest.size()>Settings.results) nearest.poll();
                        }
                    } else unloaded++;
                }
                if(++y>Math.min(255,origin.getY()+radius)) {
                    y=Math.max(0,origin.getY()-radius);
                    if(++z>origin.getZ()+radius) { z=origin.getZ()-radius; x++; }
                }
            }
            if(x<=origin.getX()+radius) return false;
            List<BlockPos> sorted=new ArrayList<>(nearest); sorted.sort(Comparator.comparingDouble(pos->pos.distanceSq(origin)));
            for(BlockPos pos:sorted) if(reported.add(pos)) report("Matching block",pos);
            if(!keep) { ClientProxy.say("Block search complete: "+seen+" loaded positions inspected, "+sorted.size()+" matches shown; "+unloaded+" unavailable positions skipped"); return true; }
            if(reported.size()>4096) reported.clear();
            begin(); next=ClientProxy.ticks+100; return false;
        }
    }
    private final class Target {
        final BlockPos pos; final Entity entity; final boolean ender;
        Target(BlockPos pos,Entity entity,boolean ender) { this.pos=pos; this.entity=entity; this.ender=ender; }
        Vec3d point() { return entity==null?new Vec3d(pos).add(.5,.5,.5):entity.getPositionVector().add(0,entity.height/2,0); }
        String key() { return entity==null?pos.toLong()+"":entity.getUniqueID().toString(); }
    }
    private final class ItemSearch implements Search {
        final Predicate<ItemStack> predicate; final boolean keep;
        final Set<String> visited=new HashSet<>();
        GuiContainer screen;
        Target target,best;
        int bestCount,shown;
        boolean selecting;
        boolean waiting,ready,enderVisited;
        int sent, next, inspected, denied;
        List<Target> candidates;
        int tileIndex,entityIndex;
        boolean collected,incomplete;
        ItemSearch(Predicate<ItemStack> predicate,boolean keep) { this.predicate=predicate;this.keep=keep; }
        @Override public boolean tick() {
            if(ClientProxy.JOB.active() || !mc.player.inventory.getItemStack().isEmpty()) { release(true);ClientProxy.say("Item search interrupted by inventory activity");return true; }
            if(waiting) {
                if(target==null || target.point().distanceTo(mc.player.getPositionEyes(1))>mc.playerController.getBlockReachDistance()+.5 ||
                        target.entity==null && !mc.world.isBlockLoaded(target.pos)) { release(true);ClientProxy.say("Item search target is no longer reachable");return true; }
                if(screen!=null && mc.player.openContainer!=screen.inventorySlots) { release(false);ClientProxy.say("Container changed; item search stopped");return true; }
                if(ready && screen!=null) {
                    int count=0; int[] budget={4096};
                    for(int slot:InventoryAdapter.group(screen.inventorySlots,false,false)) count+=count(screen.inventorySlots.getSlot(slot).getStack(),0,budget);
                    inspected++;
                    if(selecting) {
                        if(count<=0) { release(true);ClientProxy.say("Selected container changed; matching items are no longer present. Search again.");return true; }
                        LookCommand.lookAt(target.point());
                        GuiContainer match=screen;
                        release(false);itemSearch=null;mc.displayGuiScreen(match);
                        ClientProxy.say("Search "+(incomplete?"incomplete":"complete")+": checked "+(inspected-1)+" inventories; opening best observed match ("+count+" items now)." );
                        return true;
                    }
                    if(count>0) {
                        if(shown++<Settings.results) report("Found "+(incomplete?"at least ":"")+count+" matching item(s)",target.entity==null?target.pos:target.entity.getPosition());
                        if(best==null || count>bestCount) { best=target;bestCount=count; }
                    }
                    closeOwned(); waiting=ready=false;screen=null;target=null;next=ClientProxy.ticks+Settings.interactionPace;
                } else if(ClientProxy.ticks-sent>=Settings.timeout) {
                    denied++; release(true);
                    ClientProxy.say("Item search incomplete: container denied or timed out. Stopped to avoid attributing a late reply to another target.");
                    return true;
                }
                return false;
            }
            if(mc.currentScreen!=null || mc.player.openContainer!=mc.player.inventoryContainer) { release(false);ClientProxy.say("Item search interrupted by an open screen");return true; }
            if(ClientProxy.ticks<next) return false;
            if(!collect()) return false;
            for(Target candidate:candidates) {
                if(visited.contains(candidate.key()) || candidate.ender && enderVisited ||
                        candidate.point().distanceTo(mc.player.getPositionEyes(1))>mc.playerController.getBlockReachDistance()) continue;
                visited.add(candidate.key()); if(candidate.ender) enderVisited=true;
                if(candidate.entity==null && mc.world.getTileEntity(candidate.pos) instanceof TileEntityChest) {
                    for(EnumFacing face:EnumFacing.HORIZONTALS) {
                        BlockPos adjacent=candidate.pos.offset(face);
                        if(mc.world.isBlockLoaded(adjacent) && mc.world.getBlockState(adjacent).getBlock()==mc.world.getBlockState(candidate.pos).getBlock()) visited.add(adjacent.toLong()+"");
                    }
                }
                openTarget(candidate);
                return false;
            }
            if(best!=null) {
                if(shown>Settings.results) ClientProxy.say((shown-Settings.results)+" additional matching inventories omitted from chat (result limit).");
                if(best.point().distanceTo(mc.player.getPositionEyes(1))>mc.playerController.getBlockReachDistance() || best.entity==null && !mc.world.isBlockLoaded(best.pos)) {
                    release(false);ClientProxy.say("Best matching container is no longer reachable; search again.");return true;
                }
                selecting=true;openTarget(best);return false;
            }
            if(keep) { candidates=null;collected=false;tileIndex=entityIndex=0;visited.clear();enderVisited=false;next=ClientProxy.ticks+20; return false; }
            ClientProxy.say((incomplete?"Incomplete item search; no match found in ":"No matching items in ")+inspected+" reachable inventories; "+denied+" denied/timed out. Unopened or remote contents remain unknown.");
            release(false);return true;
        }
        void openTarget(Target candidate) {
            target=candidate;waiting=true;ready=false;sent=ClientProxy.ticks;
            if(candidate.entity!=null) mc.playerController.interactWithEntity(mc.player,candidate.entity,EnumHand.MAIN_HAND);
            else mc.playerController.processRightClickBlock(mc.player,mc.world,candidate.pos,EnumFacing.UP,candidate.point(),EnumHand.MAIN_HAND);
        }
        boolean collect() {
            if(collected) return true;
            if(candidates==null) candidates=new ArrayList<>();
            long deadline=System.nanoTime()+Settings.searchMillis*1_000_000L;int work=0;
            while(tileIndex<mc.world.loadedTileEntityList.size() && work++<Settings.searchBlocks && System.nanoTime()<deadline) {
                TileEntity tile=mc.world.loadedTileEntityList.get(tileIndex++);
                if(tile.getClass()==TileEntityChest.class || tile.getClass()==TileEntityEnderChest.class || tile.getClass()==TileEntityShulkerBox.class)
                    candidate(new Target(tile.getPos(),null,tile instanceof TileEntityEnderChest));
            }
            if(tileIndex<mc.world.loadedTileEntityList.size()) return false;
            while(entityIndex<mc.world.loadedEntityList.size() && work++<Settings.searchBlocks && System.nanoTime()<deadline) {
                Entity entity=mc.world.loadedEntityList.get(entityIndex++);
                if(entity.getClass()==net.minecraft.entity.item.EntityMinecartChest.class) candidate(new Target(entity.getPosition(),entity,false));
            }
            if(entityIndex<mc.world.loadedEntityList.size()) return false;
            candidates.sort(Comparator.comparingDouble(candidate->candidate.point().squareDistanceTo(mc.player.getPositionEyes(1))));
            collected=true;return true;
        }
        void candidate(Target candidate) {
            if(candidate.point().distanceTo(mc.player.getPositionEyes(1))>mc.playerController.getBlockReachDistance()) return;
            if(candidates.size()<4096) candidates.add(candidate);else incomplete=true;
        }
        int count(ItemStack stack,int depth,int[] budget) {
            if(stack.isEmpty()) return 0;
            if(budget[0]--<=0) { incomplete=true;return 0; }
            int result=predicate.test(stack)?stack.getCount():0;
            if(depth<4 && stack.getItem() instanceof ItemShulkerBox && stack.hasTagCompound()) {
                NBTTagCompound block=stack.getTagCompound().getCompoundTag("BlockEntityTag");
                NBTTagList items=block.getTagList("Items",10);
                for(int i=0;i<items.tagCount() && budget[0]>0;i++) result+=count(new ItemStack(items.getCompoundTagAt(i)),depth+1,budget);
                if(budget[0]<=0) incomplete=true;
            }
            if(depth==4 && stack.getItem() instanceof ItemShulkerBox && stack.hasTagCompound() && stack.getTagCompound().getCompoundTag("BlockEntityTag").getTagList("Items",10).tagCount()>0) incomplete=true;
            return result;
        }
        void closeOwned() { if(mc.player!=null && screen!=null && mc.player.openContainer==screen.inventorySlots) mc.player.closeScreen(); }
        void release(boolean close) {
            GuiContainer previous=screen;
            boolean owned=mc.player!=null && previous!=null && mc.player.openContainer==previous.inventorySlots;
            // Clear suppression before any screen event. Vanilla closeWindow can drop a cursor
            // stack when player inventory is full; expose that real window for manual recovery.
            waiting=false;ready=false;screen=null;target=null;
            if(!close || !owned) return;
            if(!mc.player.inventory.getItemStack().isEmpty()) {
                if(itemSearch==this) itemSearch=null;
                if(mc.currentScreen==null && !mc.player.isDead) mc.displayGuiScreen(previous);
                ClientProxy.say("Item search stopped with an item on the cursor; put it away in the open inventory");
            } else mc.player.closeScreen();
        }
    }
}
