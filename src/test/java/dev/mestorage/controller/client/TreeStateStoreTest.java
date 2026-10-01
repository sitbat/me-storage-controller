package dev.mestorage.controller.client;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeStateStoreTest {
    @TempDir Path directory;

    @Test void freshStoreRestoresExactStatesAndAtomicallyReplacesThePreviousFile() throws IOException {
        String scope=TreeStateStore.scope("local:世界一","player-a","minecraft:overworld",12);
        var first=new TreeStateStore.State(Map.of("root",false,"dim:minecraft:overworld",false,
                "dev:minecraft:overworld:1,2,3",true),true);
        new TreeStateStore(directory).save(scope,first);
        assertEquals(first,new TreeStateStore(directory).load(scope).orElseThrow());
        var second=new TreeStateStore.State(Map.of("root",true,"dev:minecraft:overworld:1,2,3",false),false);
        new TreeStateStore(directory).save(scope,second);
        assertEquals(second,new TreeStateStore(directory).load(scope).orElseThrow());
        try(var files=Files.list(directory)) { assertEquals(1,files.count(),"No temporary state files may remain"); }
    }

    @Test void WorldsPlayersDimensionsAndControllersNeverShareFiles() throws IOException {
        var store=new TreeStateStore(directory);
        var state=new TreeStateStore.State(Map.of("root",false),false);
        store.save(TreeStateStore.scope("server:one","player-a","minecraft:overworld",12),state);
        assertTrue(store.load(TreeStateStore.scope("server:two","player-a","minecraft:overworld",12)).isEmpty());
        assertTrue(store.load(TreeStateStore.scope("server:one","player-b","minecraft:overworld",12)).isEmpty());
        assertTrue(store.load(TreeStateStore.scope("server:one","player-a","minecraft:the_nether",12)).isEmpty());
        assertTrue(store.load(TreeStateStore.scope("server:one","player-a","minecraft:overworld",13)).isEmpty());
        assertNotEquals(TreeStateStore.scope("a:b","c","d",1),TreeStateStore.scope("a","b:c","d",1));
    }

    @Test void MalformedStateCannotPartiallyRestoreBranches() throws IOException {
        var store=new TreeStateStore(directory);
        store.save("scope",new TreeStateStore.State(Map.of("root",false),false));
        Path file;
        try(var files=Files.list(directory)) { file=files.findFirst().orElseThrow(); }
        Files.writeString(file,"version=1\nbranch.root=false\nbranch.dev\\:broken=not-a-boolean\n",StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->store.load("scope"));
    }

    @Test void RestoreBeforeFirstDirectoryDoesNotDiscardUnknownBranchesOrAnimateThemOpen() {
        var tree=new StorageTree((device,slot)->fail("Restoring expansion must not navigate or send a request"));
        var saved=Map.of("root",false,"dim:minecraft:overworld",false,"dev:arrives-in-a-later-generation",true);
        tree.restoreExpansionState(saved);
        assertEquals(saved,tree.expansionState());
        assertFalse(tree.isOpen("root"));
        assertFalse(tree.isOpen("dim:minecraft:overworld"));
        assertTrue(tree.isOpen("dev:arrives-in-a-later-generation"));
        assertEquals(0,tree.scrollOffset());
        tree.revealRoot(); // Explicit navigation is still allowed to open the saved closed root.
        assertTrue(tree.isOpen("root"));
        assertTrue(tree.isOpen("dev:arrives-in-a-later-generation"));
    }
}
