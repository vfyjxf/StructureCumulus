package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandRouterTest {

    @Test
    void registersCheckAndSettleUnderCumulus() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CumulusCommand.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null));
        CommandNode<CommandSourceStack> cumulus = dispatcher.getRoot().getChild("cumulus");
        assertNotNull(cumulus, "the cumulus root");
        assertNotNull(cumulus.getChild("check"), "the check subcommand");
        CommandNode<CommandSourceStack> settle = cumulus.getChild("settle");
        assertNotNull(settle, "the settle subcommand");
        assertNotNull(settle.getChild("id"), "settle takes an id");
        assertNotNull(settle.getChild("id").getChild("origin"), "settle takes an origin");
    }
}
