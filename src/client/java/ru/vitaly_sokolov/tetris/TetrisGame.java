package ru.vitaly_sokolov.tetris;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Standalone classic Tetris rules. Rendering and persistence live elsewhere. */
public final class TetrisGame {
    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;

    // Seven classic tetrominoes, represented in a 4x4 rotation box.
    private static final int[][][] SHAPES = {
        {{1, 0}, {2, 0}, {1, 1}, {2, 1}},       // O
        {{0, 1}, {1, 1}, {2, 1}, {3, 1}},       // I
        {{1, 0}, {0, 1}, {1, 1}, {2, 1}},       // T
        {{1, 0}, {2, 0}, {0, 1}, {1, 1}},       // S
        {{0, 0}, {1, 0}, {1, 1}, {2, 1}},       // Z
        {{0, 0}, {0, 1}, {1, 1}, {2, 1}},       // J
        {{2, 0}, {0, 1}, {1, 1}, {2, 1}}        // L
    };

    private final Random random = new Random();
    private int[][] board = new int[HEIGHT][WIDTH];
    private final List<Integer> nextPieces = new ArrayList<>();
    private final List<Integer> bag = new ArrayList<>();

    private int currentType = -1;
    private int rotation;
    private int x;
    private int y;
    private int[] materialIds = {0, 1, 2, 3};

    private long score;
    private int lines;
    private int level = 1;
    private int combo;
    private boolean started;
    private boolean paused = true;
    private boolean gameOver;
    private boolean resultRecorded;
    private long lastDropAt;

    public void newGame() {
        board = new int[HEIGHT][WIDTH];
        nextPieces.clear();
        bag.clear();
        score = 0;
        lines = 0;
        level = 1;
        combo = 0;
        gameOver = false;
        resultRecorded = false;
        started = true;
        paused = false;
        lastDropAt = System.currentTimeMillis();
        ensurePreview();
        spawnNext();
    }

    public void resume() {
        if (started && !gameOver) {
            paused = false;
            lastDropAt = System.currentTimeMillis();
        }
    }

    public void pause() {
        if (started && !gameOver) {
            paused = true;
        }
    }

    public void tick(long now) {
        if (!started || paused || gameOver) return;
        if (now - lastDropAt >= getDropIntervalMs()) {
            lastDropAt = now;
            dropOne(false);
        }
    }

    public void moveLeft() {
        if (canAcceptInput() && !collides(currentType, rotation, x - 1, y)) x--;
    }

    public void moveRight() {
        if (canAcceptInput() && !collides(currentType, rotation, x + 1, y)) x++;
    }

    public void rotateClockwise() {
        if (!canAcceptInput() || currentType == 0) return;
        int nextRotation = (rotation + 1) & 3;
        for (int kick : new int[] {0, -1, 1, -2, 2}) {
            if (!collides(currentType, nextRotation, x + kick, y)) {
                rotation = nextRotation;
                x += kick;
                return;
            }
        }
    }

    public void softDrop() {
        if (canAcceptInput()) {
            dropOne(true);
            lastDropAt = System.currentTimeMillis();
        }
    }

    public void hardDrop() {
        if (!canAcceptInput()) return;
        int distance = 0;
        while (!collides(currentType, rotation, x, y + 1)) {
            y++;
            distance++;
        }
        score += distance * 2L;
        lockCurrent();
        lastDropAt = System.currentTimeMillis();
    }

    private boolean canAcceptInput() {
        return started && !paused && !gameOver && currentType >= 0;
    }

    private boolean dropOne(boolean soft) {
        if (!collides(currentType, rotation, x, y + 1)) {
            y++;
            if (soft) score++;
            return true;
        }
        lockCurrent();
        return false;
    }

    private void lockCurrent() {
        for (Cell cell : cellsAtCurrentPosition()) {
            int boardX = x + cell.x();
            int boardY = y + cell.y();
            if (boardY < 0) {
                finishGame();
                return;
            }
            if (boardX >= 0 && boardX < WIDTH && boardY < HEIGHT) {
                board[boardY][boardX] = cell.materialId() + 1;
            }
        }

        int cleared = clearLines();
        if (cleared > 0) {
            int[] points = {0, 100, 300, 500, 800};
            score += (long) points[Math.min(cleared, 4)] * level;
            lines += cleared;
            combo++;
            if (combo > 1) score += (long) (combo - 1) * 50 * level;
            level = Math.min(1000, 1 + lines / 10);
        } else {
            combo = 0;
        }
        spawnNext();
    }

    private int clearLines() {
        int cleared = 0;
        for (int row = HEIGHT - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < WIDTH; col++) {
                if (board[row][col] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int move = row; move > 0; move--) {
                    board[move] = Arrays.copyOf(board[move - 1], WIDTH);
                }
                board[0] = new int[WIDTH];
                row++;
            }
        }
        return cleared;
    }

    private void spawnNext() {
        ensurePreview();
        currentType = nextPieces.remove(0);
        ensurePreview();
        rotation = 0;
        x = 3;
        y = 0;
        materialIds = new int[] {
            random.nextInt(10), random.nextInt(10),
            random.nextInt(10), random.nextInt(10)
        };
        if (collides(currentType, rotation, x, y)) finishGame();
    }

    private void ensurePreview() {
        while (nextPieces.size() < 3) {
            if (bag.isEmpty()) {
                for (int type = 0; type < SHAPES.length; type++) bag.add(type);
                Collections.shuffle(bag, random);
            }
            nextPieces.add(bag.remove(0));
        }
    }

    private void finishGame() {
        gameOver = true;
        paused = true;
    }

    private boolean collides(int type, int turn, int offsetX, int offsetY) {
        if (type < 0) return false;
        for (int[] point : rotatedShape(type, turn)) {
            int boardX = offsetX + point[0];
            int boardY = offsetY + point[1];
            if (boardX < 0 || boardX >= WIDTH || boardY >= HEIGHT) return true;
            if (boardY >= 0 && board[boardY][boardX] != 0) return true;
        }
        return false;
    }

    private static int[][] rotatedShape(int type, int turn) {
        int[][] result = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int px = SHAPES[type][i][0];
            int py = SHAPES[type][i][1];
            for (int r = 0; r < (turn & 3); r++) {
                int nextX = py;
                int nextY = 3 - px;
                px = nextX;
                py = nextY;
            }
            result[i][0] = px;
            result[i][1] = py;
        }
        return result;
    }

    private List<Cell> cellsAtCurrentPosition() {
        List<Cell> cells = new ArrayList<>(4);
        int[][] shape = rotatedShape(currentType, rotation);
        for (int i = 0; i < 4; i++) cells.add(new Cell(shape[i][0], shape[i][1], materialIds[i]));
        return cells;
    }

    public List<Cell> getCurrentCells() {
        return currentType < 0 ? List.of() : cellsAtCurrentPosition();
    }

    public List<Cell> getGhostCells() {
        return currentType < 0 || gameOver ? List.of() : cellsAtCurrentPosition();
    }

    public int getGhostY() {
        int ghostY = y;
        if (currentType >= 0) {
            while (!collides(currentType, rotation, x, ghostY + 1)) ghostY++;
        }
        return ghostY;
    }

    public static int[][] getShapeCells(int type) {
        return type < 0 || type >= SHAPES.length ? new int[0][0] : rotatedShape(type, 0);
    }

    public int[][] getBoard() { return board; }
    public List<Integer> getNextPieces() { return List.copyOf(nextPieces); }
    public long getScore() { return score; }
    public int getLines() { return lines; }
    public int getLevel() { return level; }
    public int getCombo() { return combo; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean hasStarted() { return started; }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return gameOver; }
    public boolean isResultRecorded() { return resultRecorded; }
    public void markResultRecorded() { resultRecorded = true; }

    public long getDropIntervalMs() {
        return Math.max(180L, Math.round(900.0 - (level - 1) * 0.72));
    }

    public Snapshot snapshot() {
        Snapshot snapshot = new Snapshot();
        snapshot.board = new int[HEIGHT][WIDTH];
        for (int row = 0; row < HEIGHT; row++) snapshot.board[row] = Arrays.copyOf(board[row], WIDTH);
        snapshot.currentType = currentType;
        snapshot.rotation = rotation;
        snapshot.x = x;
        snapshot.y = y;
        snapshot.materialIds = Arrays.copyOf(materialIds, 4);
        snapshot.nextPieces = new ArrayList<>(nextPieces);
        snapshot.bag = new ArrayList<>(bag);
        snapshot.score = score;
        snapshot.lines = lines;
        snapshot.level = level;
        snapshot.combo = combo;
        snapshot.started = started;
        snapshot.paused = paused;
        snapshot.gameOver = gameOver;
        snapshot.resultRecorded = resultRecorded;
        return snapshot;
    }

    public static TetrisGame fromSnapshot(Snapshot snapshot) {
        TetrisGame game = new TetrisGame();
        if (snapshot == null) return game;
        if (snapshot.board != null && snapshot.board.length == HEIGHT) {
            for (int row = 0; row < HEIGHT; row++) {
                if (snapshot.board[row] != null && snapshot.board[row].length == WIDTH) {
                    game.board[row] = Arrays.copyOf(snapshot.board[row], WIDTH);
                }
            }
        }
        game.currentType = snapshot.currentType >= 0 && snapshot.currentType < SHAPES.length
            ? snapshot.currentType : -1;
        game.rotation = snapshot.rotation & 3;
        game.x = snapshot.x;
        game.y = snapshot.y;
        if (snapshot.materialIds != null && snapshot.materialIds.length == 4) {
            game.materialIds = Arrays.copyOf(snapshot.materialIds, 4);
        }
        if (snapshot.nextPieces != null) {
            for (int piece : snapshot.nextPieces) {
                if (piece >= 0 && piece < SHAPES.length && game.nextPieces.size() < 3) game.nextPieces.add(piece);
            }
        }
        if (snapshot.bag != null) {
            for (int piece : snapshot.bag) if (piece >= 0 && piece < SHAPES.length) game.bag.add(piece);
        }
        game.score = Math.max(0, snapshot.score);
        game.lines = Math.max(0, snapshot.lines);
        game.level = Math.max(1, Math.min(1000, snapshot.level));
        game.combo = Math.max(0, snapshot.combo);
        game.started = snapshot.started;
        game.paused = true;
        game.gameOver = snapshot.gameOver;
        game.resultRecorded = snapshot.resultRecorded;
        game.lastDropAt = System.currentTimeMillis();
        return game;
    }

    public record Cell(int x, int y, int materialId) {}

    public static final class Snapshot {
        public int[][] board;
        public int currentType;
        public int rotation;
        public int x;
        public int y;
        public int[] materialIds;
        public List<Integer> nextPieces;
        public List<Integer> bag;
        public long score;
        public int lines;
        public int level;
        public int combo;
        public boolean started;
        public boolean paused;
        public boolean gameOver;
        public boolean resultRecorded;
    }
}
