package ru.vitaly_sokolov.tetris;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TetrisStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Path file;
    private PersistentData data;

    public TetrisStorage() {
        file = FabricLoader.getInstance().getConfigDir().resolve("vitaly-tetris.json");
        data = read();
        if (data.records == null) data.records = new ArrayList<>();
    }

    public TetrisGame loadGame() {
        return data.game == null ? new TetrisGame() : TetrisGame.fromSnapshot(data.game);
    }

    public List<ScoreRecord> getRecords() {
        return List.copyOf(data.records);
    }

    public void save(TetrisGame game) {
        data.game = game.snapshot();
        write();
    }

    public void addRecord(TetrisGame game) {
        if (game.isResultRecorded()) return;
        game.markResultRecorded();
        if (game.getScore() > 0 || game.getLines() > 0) {
            data.records.add(new ScoreRecord(
                game.getScore(), game.getLevel(), game.getLines(),
                LocalDateTime.now().format(DATE_FORMAT)
            ));
            data.records.sort(Comparator.comparingLong((ScoreRecord record) -> record.score).reversed());
            while (data.records.size() > 10) data.records.remove(data.records.size() - 1);
        }
        save(game);
    }

    private PersistentData read() {
        if (!Files.exists(file)) return new PersistentData();
        try {
            PersistentData loaded = GSON.fromJson(Files.readString(file), PersistentData.class);
            return loaded == null ? new PersistentData() : loaded;
        } catch (Exception exception) {
            System.err.println("[Vitaly Tetris] Could not read save: " + exception.getMessage());
            return new PersistentData();
        }
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data));
        } catch (IOException exception) {
            System.err.println("[Vitaly Tetris] Could not write save: " + exception.getMessage());
        }
    }

    public static final class PersistentData {
        public TetrisGame.Snapshot game;
        public List<ScoreRecord> records = new ArrayList<>();
    }

    public static final class ScoreRecord {
        public long score;
        public int level;
        public int lines;
        public String date;

        public ScoreRecord(long score, int level, int lines, String date) {
            this.score = score;
            this.level = level;
            this.lines = lines;
            this.date = date;
        }
    }
}
