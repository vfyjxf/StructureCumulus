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
    void registersEverySubcommandUnderCumulus() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CumulusCommand.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null));
        CommandNode<CommandSourceStack> cumulus = dispatcher.getRoot().getChild("cumulus");
        assertNotNull(cumulus, "the cumulus root");
        assertNotNull(cumulus.getChild("check"), "the check subcommand");
        CommandNode<CommandSourceStack> settle = cumulus.getChild("settle");
        assertNotNull(settle, "the settle subcommand");
        assertNotNull(settle.getChild("id"), "settle takes an id");
        assertNotNull(settle.getChild("id").getChild("origin"), "settle takes an origin");
        CommandNode<CommandSourceStack> compile = cumulus.getChild("compile");
        assertNotNull(compile, "the compile subcommand");
        assertNotNull(compile.getChild("id"), "compile takes an id");
        CommandNode<CommandSourceStack> explain = cumulus.getChild("explain");
        assertNotNull(explain, "the explain subcommand");
        assertNotNull(explain.getChild("code"), "explain takes a code");
        CommandNode<CommandSourceStack> claims = cumulus.getChild("claims");
        assertNotNull(claims, "the claims subcommand");
        assertNotNull(claims.getChild("from"), "claims takes a from corner");
        assertNotNull(claims.getChild("from").getChild("to"), "claims takes a to corner");
        CommandNode<CommandSourceStack> set = cumulus.getChild("set");
        assertNotNull(set, "the set subcommand group");
        for (String path : new String[]{"check", "settle"}) {
            CommandNode<CommandSourceStack> verb = set.getChild(path);
            assertNotNull(verb, "set takes a " + path + " path");
            assertNotNull(verb.getChild("id"), path + " takes an id");
            assertNotNull(verb.getChild("id").getChild("origin"), path + " takes an origin");
        }
        CommandNode<CommandSourceStack> build = cumulus.getChild("build");
        assertNotNull(build, "the build subcommand");
        assertNotNull(build.getChild("undo"), "build takes undo");
        CommandNode<CommandSourceStack> source = build.getChild("id").getChild("origin").getChild("with");
        assertNotNull(source, "build takes a with form");
        for (String form : new String[]{"creative", "player", "container", "source"}) {
            assertNotNull(source.getChild(form), "with takes " + form);
        }
        assertNotNull(
            source.getChild("source").getChild("--settle"),
            "the registered source form chains the settle flag"
        );
        assertNotNull(build.getChild("id").getChild("origin").getChild("--settle"), "the single build takes --settle");
        assertNotNull(
            set.getChild("build").getChild("id").getChild("origin").getChild("with").getChild("source"),
            "the set build shares the registered source form"
        );
        CommandNode<CommandSourceStack> prefs = cumulus.getChild("prefs");
        assertNotNull(prefs, "the prefs subcommand");
        assertNotNull(prefs.getChild("show"), "prefs takes show");
        CommandNode<CommandSourceStack> setPref = prefs.getChild("set");
        assertNotNull(setPref, "prefs takes set");
        CommandNode<CommandSourceStack> key = setPref.getChild("key");
        assertNotNull(key, "prefs set takes a key");
        CommandNode<CommandSourceStack> value = key.getChild("value");
        assertNotNull(value, "prefs set takes a value");
        assertNotNull(value.getCommand(), "prefs set <key> <value> runs");
        CommandNode<CommandSourceStack> forget = prefs.getChild("forget");
        assertNotNull(forget, "prefs takes forget");
        assertNotNull(forget.getCommand(), "prefs forget runs without an id");
        assertNotNull(forget.getChild("id"), "prefs forget takes an optional id");
        assertNotNull(forget.getChild("id").getCommand(), "prefs forget <id> runs");
    }
}
