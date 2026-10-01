package dev.mestorage.controller.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Client-owned state; callers supply the scope independently of directory contents. */
final class TreeStateStore {
    record State(Map<String,Boolean> branches,boolean panelCollapsed) {
        State { branches=Map.copyOf(branches); }
    }
    private final Path directory;
    TreeStateStore(Path directory) { this.directory=directory; }
    static String scope(String world,String player,String dimension,long position) {
        // Length prefixes avoid delimiter collisions in user-provided paths or server names.
        return world.length()+":"+world+player.length()+":"+player+dimension.length()+":"+dimension+":"+position;
    }

    Optional<State> load(String scope) throws IOException {
        Path file=file(scope);
        if(!Files.exists(file)) return Optional.empty();
        var values=new Properties();
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { values.load(reader); }
        catch(IllegalArgumentException failure) { throw new IOException("Invalid tree state",failure); }
        if(!"1".equals(values.getProperty("version"))) return Optional.empty();
        var branches=new HashMap<String,Boolean>();
        for(String name:values.stringPropertyNames()) {
            if(!name.startsWith("branch.")) continue;
            String value=values.getProperty(name);
            if(!value.equals("true")&&!value.equals("false")) throw new IOException("Invalid tree branch state");
            branches.put(name.substring(7),Boolean.parseBoolean(value));
        }
        return Optional.of(new State(branches,Boolean.parseBoolean(values.getProperty("panelCollapsed","false"))));
    }

    void save(String scope,State state) throws IOException {
        Files.createDirectories(directory);
        var values=new Properties(); values.setProperty("version","1");
        values.setProperty("panelCollapsed",Boolean.toString(state.panelCollapsed()));
        state.branches().forEach((key,value)->values.setProperty("branch."+key,value.toString()));
        Path file=file(scope),temporary=Files.createTempFile(directory,"tree-",".tmp");
        try {
            try(var writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)) {
                values.store(writer,"ME Storage Controller client tree state");
            }
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path file(String scope) {
        try {
            var digest=MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest)+".properties");
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
