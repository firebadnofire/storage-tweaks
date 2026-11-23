package org.firebadnofire.storagetweaks.server;

import java.util.List;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.server.permission.*;
import org.firebadnofire.storagetweaks.Settings;

public final class PunchDepositCommand extends CommandBase {
    public static final String PERMISSION="firebadnofire_storage_tweaks.command.punchdeposit";
    public static void registerPermission() {
        PermissionAPI.registerNode(PERMISSION,DefaultPermissionLevel.OP,"Change global Storage Tweaks punch deposit availability");
    }
    @Override public String getName() { return "punchdeposit"; }
    @Override public String getUsage(ICommandSender sender) { return "/punchdeposit <true|false>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) {
        if(sender instanceof EntityPlayer) {
            if(server.isSinglePlayer() && sender.getName().equals(server.getServerOwner())) return true;
            return PermissionAPI.hasPermission((EntityPlayer)sender,PERMISSION);
        }
        return sender.canUseCommand(2,getName());
    }
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        if(!checkPermission(server,sender)) throw new CommandException("commands.generic.permission");
        if(args.length!=1 || !(args[0].equals("true") || args[0].equals("false"))) throw new WrongUsageException(getUsage(sender));
        boolean enabled=Boolean.parseBoolean(args[0]);
        Settings.config.get("server","enabled",true).set(enabled);
        Settings.config.save();Settings.serverPunch=enabled;
        PunchNetwork.refreshCapabilities(server);
        sender.sendMessage(new TextComponentString("[Storage Tweaks] Server punch deposit "+(enabled?"enabled":"disabled")+" (saved). Access and protection checks still apply."));
        if(enabled) {
            String blocker=PunchAccess.blocker();
            if(!blocker.isEmpty()) sender.sendMessage(new TextComponentString("[Storage Tweaks] Punch is still unavailable: "+blocker));
        }
    }
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos) {
        return args.length==1?getListOfStringsMatchingLastWord(args,"true","false"):java.util.Collections.emptyList();
    }
}
