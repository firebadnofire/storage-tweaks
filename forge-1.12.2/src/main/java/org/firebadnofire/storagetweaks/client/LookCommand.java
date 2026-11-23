package org.firebadnofire.storagetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraftforge.client.ClientCommandHandler;
import org.firebadnofire.storagetweaks.StorageTweaks;

final class LookCommand extends CommandBase {
    static void register() {
        if(ClientCommandHandler.instance.getCommands().containsKey("clook")) {
            StorageTweaks.LOG.warn("Not registering /clook: another mod owns this command");return;
        }
        ClientCommandHandler.instance.registerCommand(new LookCommand());
    }
    @Override public String getName() { return "clook"; }
    @Override public String getUsage(ICommandSender sender) { return "/clook <x> <y> <z>"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) { return true; }
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        if(args.length!=3) throw new WrongUsageException(getUsage(sender));
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null) throw new CommandException("Join a world before using /clook");
        BlockPos target=parseBlockPos(sender,args,0,false);
        lookAt(new Vec3d(target).add(.5,.5,.5));
    }
    static void lookAt(Vec3d point) {
        Minecraft mc=Minecraft.getMinecraft();
        Vec3d delta=point.subtract(mc.player.getPositionEyes(1));
        if(delta.lengthSquared()<1e-12) return;
        float yaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90);
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        mc.player.rotationYawHead=mc.player.prevRotationYawHead=yaw;
    }
}

