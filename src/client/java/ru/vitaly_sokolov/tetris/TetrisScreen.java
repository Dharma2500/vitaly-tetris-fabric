package ru.vitaly_sokolov.tetris;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class TetrisScreen extends Screen {
    private static final int GOLD = 0xFFE0B65E;
    private static final int PALE_GOLD = 0xFFFFD98A;
    private static final int MUTED_GOLD = 0xFF8D6B35;
    private static final Block[] MATERIALS = {
        Blocks.POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.QUARTZ_BLOCK,
        Blocks.GOLD_BLOCK, Blocks.COPPER_BLOCK, Blocks.AMETHYST_BLOCK,
        Blocks.PRISMARINE_BRICKS, Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK,
        Blocks.DEEPSLATE_TILES
    };

    private enum View { MENU, GAME, RECORDS }

    private record UiButton(String id, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final TetrisStorage storage;
    private final TetrisGame game;
    private final TextureAtlasSprite[] spriteCache = new TextureAtlasSprite[MATERIALS.length];
    private final List<UiButton> buttons = new ArrayList<>();

    private View view;
    private boolean confirmReset;
    private long lastSaveAt;
    private int panelX, panelY, panelWidth, panelHeight;
    private int boardX, boardY, cellSize;

    public TetrisScreen() {
        super(Component.literal("Tetris by Vitaly_Sokolov"));
        storage = new TetrisStorage();
        game = storage.loadGame();
        view = game.hasStarted() ? View.GAME : View.MENU;
        lastSaveAt = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        game.tick(System.currentTimeMillis());
        if (game.isGameOver() && !game.isResultRecorded()) storage.addRecord(game);
        long now = System.currentTimeMillis();
        if (now - lastSaveAt >= 5000L) {
            storage.save(game);
            lastSaveAt = now;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        calculateLayout();
        buttons.clear();
        graphics.fill(0, 0, width, height, 0xC9000000);
        drawMarblePanel(graphics);

        if (view == View.RECORDS) drawRecords(graphics, mouseX, mouseY);
        else if (!game.hasStarted()) drawMenu(graphics, mouseX, mouseY);
        else drawGame(graphics, mouseX, mouseY);

        if (confirmReset) drawResetConfirmation(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void calculateLayout() {
        int byScreen = Math.max(280, width * 34 / 100);
        int byHeight = Math.max(280, (height - 24) * 9 / 16);
        panelWidth = Math.min(620, Math.min(byScreen, byHeight));
        panelHeight = panelWidth * 16 / 9;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        cellSize = Math.max(8, Math.min((panelWidth - 64) / 10, (panelHeight - 300) / 20));
        boardX = panelX + (panelWidth - cellSize * TetrisGame.WIDTH) / 2;
        boardY = panelY + panelHeight / 6;
    }

    private void drawMarblePanel(GuiGraphics graphics) {
        graphics.fill(panelX - 7, panelY - 7, panelX + panelWidth + 7, panelY + panelHeight + 7, 0xB6000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF100D0A);
        graphics.fill(panelX + 3, panelY + 3, panelX + panelWidth - 3, panelY + panelHeight - 3, 0xFF15110D);
        for (int i = 0; i < 7; i++) {
            int x1 = panelX + 8 + i * (panelWidth / 7);
            int x2 = panelX + Math.min(panelWidth - 8, x1 + panelWidth / 3);
            int y2 = panelY + panelHeight - 20 - (i % 3) * 24;
            drawLine(graphics, x1, panelY + 18, x2, y2, 0x1FCA9B46);
        }
        fillBorder(graphics, panelX, panelY, panelWidth, panelHeight, 2, GOLD);
        fillBorder(graphics, panelX + 7, panelY + 7, panelWidth - 14, panelHeight - 14, 1, 0xFF76572C);
        drawCorner(graphics, true, true);
        drawCorner(graphics, false, true);
        drawCorner(graphics, true, false);
        drawCorner(graphics, false, false);
    }

    private void drawMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);
        int center = panelX + panelWidth / 2;
        int emblemY = panelY + panelHeight / 3;
        drawCentered(graphics, "THE CLASSIC BLOCK GAME", center, emblemY - 35, 0xFFB8A27A);
        drawTetrominoEmblem(graphics, center, emblemY + 12);
        drawCentered(graphics, "10 × 20 FIELD  •  1000 LEVELS", center, emblemY + 100, PALE_GOLD);
        drawCentered(graphics, "A quiet little break between adventures.", center, emblemY + 124, 0xFFB7AC9B);

        int buttonWidth = panelWidth - 92;
        int buttonX = center - buttonWidth / 2;
        int firstY = panelY + panelHeight - 150;
        addButton(graphics, "start", "НОВАЯ ИГРА", buttonX, firstY, buttonWidth, 38, mouseX, mouseY);
        addButton(graphics, "records", "ЛИЧНЫЕ РЕКОРДЫ", buttonX, firstY + 48, buttonWidth, 38, mouseX, mouseY);
        drawCentered(graphics, "← → движение     ↑ поворот     ↓ ускорение", center, panelY + panelHeight - 42, 0xFF988A72);
        drawCentered(graphics, "SPACE — сброс     ESC — пауза и выход", center, panelY + panelHeight - 25, 0xFF988A72);
    }

    private void drawGame(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);
        int center = panelX + panelWidth / 2;
        int statsY = panelY + 76;
        drawStat(graphics, "СЧЁТ", formatScore(game.getScore()), panelX + panelWidth / 6, statsY);
        drawStat(graphics, "УРОВЕНЬ", String.valueOf(game.getLevel()), center, statsY);
        drawStat(graphics, "ЛИНИИ / КОМБО", game.getLines() + " / ×" + game.getCombo(), panelX + panelWidth * 5 / 6, statsY);

        drawBoard(graphics);
        drawNext(graphics, boardY + cellSize * TetrisGame.HEIGHT + 13);

        if (game.isGameOver()) {
            drawOverlay(graphics, "ИГРА ОКОНЧЕНА", "Счёт: " + formatScore(game.getScore()), "Нажми «Сначала» для новой партии");
        } else if (game.isPaused()) {
            drawOverlay(graphics, "ПАУЗА", "Прогресс сохранён", "Продолжи, когда будешь готов");
        }

        int buttonY = panelY + panelHeight - 57;
        int gap = 7;
        int buttonWidth = (panelWidth - 48 - gap * 2) / 3;
        String primaryId = game.isPaused() ? "resume" : "pause";
        String primaryLabel = game.isGameOver() ? "СНАЧАЛА" : (game.isPaused() ? "ПРОДОЛЖИТЬ" : "ПАУЗА");
        addButton(graphics, primaryId, primaryLabel, panelX + 24, buttonY, buttonWidth, 34, mouseX, mouseY);
        addButton(graphics, "new", "НОВАЯ", panelX + 24 + buttonWidth + gap, buttonY, buttonWidth, 34, mouseX, mouseY);
        addButton(graphics, "records", "РЕКОРДЫ", panelX + 24 + (buttonWidth + gap) * 2, buttonY, buttonWidth, 34, mouseX, mouseY);
        drawControls(graphics, buttonY);
    }

    private void drawBoard(GuiGraphics graphics) {
        int boardWidth = cellSize * TetrisGame.WIDTH;
        int boardHeight = cellSize * TetrisGame.HEIGHT;
        graphics.fill(boardX - 5, boardY - 5, boardX + boardWidth + 5, boardY + boardHeight + 5, 0xFF050505);
        fillBorder(graphics, boardX - 5, boardY - 5, boardWidth + 10, boardHeight + 10, 1, MUTED_GOLD);
        graphics.fill(boardX, boardY, boardX + boardWidth, boardY + boardHeight, 0xFF090909);

        int[][] board = game.getBoard();
        for (int row = 0; row < TetrisGame.HEIGHT; row++) {
            for (int col = 0; col < TetrisGame.WIDTH; col++) {
                int x = boardX + col * cellSize;
                int y = boardY + row * cellSize;
                graphics.fill(x, y, x + cellSize - 1, y + cellSize - 1, 0xFF171411);
                if (board[row][col] > 0) drawMaterial(graphics, board[row][col] - 1, x, y, cellSize);
            }
        }

        if (!game.isGameOver()) {
            int ghostY = game.getGhostY();
            for (TetrisGame.Cell cell : game.getGhostCells()) {
                int row = ghostY + cell.y();
                int col = game.getX() + cell.x();
                if (insideBoard(row, col)) {
                    int x = boardX + col * cellSize;
                    int y = boardY + row * cellSize;
                    graphics.fill(x + 3, y + 3, x + cellSize - 3, y + cellSize - 3, 0x665E4825);
                    fillBorder(graphics, x + 2, y + 2, cellSize - 4, cellSize - 4, 1, 0x997F642F);
                }
            }
            for (TetrisGame.Cell cell : game.getCurrentCells()) {
                int row = game.getY() + cell.y();
                int col = game.getX() + cell.x();
                if (insideBoard(row, col)) {
                    drawMaterial(graphics, cell.materialId(), boardX + col * cellSize, boardY + row * cellSize, cellSize);
                }
            }
        }

        if (game.isLineClearAnimating()) {
            drawLineClearEffect(graphics, System.currentTimeMillis());
        }
    }

    private void drawLineClearEffect(GuiGraphics graphics, long now) {
        float progress = game.getLineClearProgress(now);
        float pulse = (float) Math.sin(progress * Math.PI);
        int alpha = Math.round(pulse * 48.0F);
        int glow = (alpha << 24) | 0x00E4B85E;
        int boardWidth = cellSize * TetrisGame.WIDTH;
        int sweepX = boardX + Math.min(boardWidth - 2, Math.round(progress * boardWidth));

        for (int row : game.getClearingRows()) {
            int y = boardY + row * cellSize;
            graphics.fill(boardX, y, boardX + boardWidth, y + cellSize, glow);
            graphics.fill(sweepX, y, sweepX + 2, y + cellSize, 0x88FFE7A5);
        }
    }

    private boolean insideBoard(int row, int col) {
        return row >= 0 && row < TetrisGame.HEIGHT && col >= 0 && col < TetrisGame.WIDTH;
    }

    private void drawNext(GuiGraphics graphics, int y) {
        int center = panelX + panelWidth / 2;
        drawCentered(graphics, "СЛЕДУЮЩИЕ ФИГУРЫ", center, y, 0xFFBFA574);
        List<Integer> next = game.getNextPieces();
        int mini = Math.max(4, Math.min(7, cellSize / 5));
        int gap = 20;
        int groupWidth = mini * 4 + gap;
        int startX = center - (groupWidth * 3 - gap) / 2;
        int boxY = y + 17;
        for (int i = 0; i < Math.min(3, next.size()); i++) {
            int[][] shape = TetrisGame.getShapeCells(next.get(i));
            int originX = startX + i * groupWidth;
            for (int[] point : shape) {
                drawMaterial(graphics, (next.get(i) + point[0] + point[1]) % MATERIALS.length,
                    originX + point[0] * mini, boxY + point[1] * mini, mini);
            }
        }
    }

    private void drawRecords(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);
        int center = panelX + panelWidth / 2;
        drawCentered(graphics, "ТАБЛИЦА ЛИЧНЫХ РЕКОРДОВ", center, panelY + 88, PALE_GOLD);
        drawCentered(graphics, "ТОП 10 ЛУЧШИХ ПАРТИЙ", center, panelY + 111, 0xFF9D8A69);

        int top = panelY + 155;
        int rowHeight = Math.max(34, Math.min(48, panelHeight / 19));
        int left = panelX + 25;
        int right = panelX + panelWidth - 25;
        graphics.fill(left, top, right, top + 30, 0xFF292116);
        graphics.drawString(font, "#", left + 8, top + 10, PALE_GOLD);
        graphics.drawString(font, "СЧЁТ", left + 35, top + 10, PALE_GOLD);
        graphics.drawString(font, "УР.", left + panelWidth / 2 - 25, top + 10, PALE_GOLD);
        graphics.drawString(font, "ЛИНИИ", left + panelWidth / 2 + 22, top + 10, PALE_GOLD);

        List<TetrisStorage.ScoreRecord> records = storage.getRecords();
        if (records.isEmpty()) {
            drawCentered(graphics, "Пока нет записей. Первая партия ждёт тебя.", center, top + 90, 0xFFB8AC9A);
        } else {
            for (int i = 0; i < records.size(); i++) {
                TetrisStorage.ScoreRecord record = records.get(i);
                int rowY = top + 31 + i * rowHeight;
                if (rowY + rowHeight > panelY + panelHeight - 105) break;
                if ((i & 1) == 0) graphics.fill(left, rowY, right, rowY + rowHeight, 0x553B2D18);
                graphics.drawString(font, String.valueOf(i + 1), left + 8, rowY + 11, 0xFFBFA574);
                graphics.drawString(font, formatScore(record.score), left + 35, rowY + 11, 0xFFF0E4CF);
                graphics.drawString(font, String.valueOf(record.level), left + panelWidth / 2 - 25, rowY + 11, 0xFFF0E4CF);
                graphics.drawString(font, String.valueOf(record.lines), left + panelWidth / 2 + 35, rowY + 11, 0xFFF0E4CF);
                if (record.date != null) drawCentered(graphics, record.date, center, rowY + rowHeight - 10, 0xFF8F826D);
            }
        }
        int buttonWidth = panelWidth - 92;
        addButton(graphics, "back", "НАЗАД", center - buttonWidth / 2,
            panelY + panelHeight - 70, buttonWidth, 38, mouseX, mouseY);
    }

    private void drawResetConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        int boxWidth = panelWidth - 56;
        int boxHeight = 150;
        int x = panelX + (panelWidth - boxWidth) / 2;
        int y = panelY + (panelHeight - boxHeight) / 2;
        graphics.fill(x - 4, y - 4, x + boxWidth + 4, y + boxHeight + 4, GOLD);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xFF100D09);
        drawCentered(graphics, "НАЧАТЬ НОВУЮ ИГРУ?", panelX + panelWidth / 2, y + 28, PALE_GOLD);
        drawCentered(graphics, "Текущая партия будет заменена.", panelX + panelWidth / 2, y + 55, 0xFFC5B9A6);
        int gap = 10;
        int buttonWidth = (boxWidth - 30 - gap) / 2;
        addButton(graphics, "cancel_reset", "ОТМЕНА", x + 15, y + 90, buttonWidth, 36, mouseX, mouseY);
        addButton(graphics, "confirm_reset", "НОВАЯ ИГРА", x + 15 + buttonWidth + gap, y + 90,
            buttonWidth, 36, mouseX, mouseY);
    }

    private void drawOverlay(GuiGraphics graphics, String title, String line1, String line2) {
        int boardWidth = cellSize * TetrisGame.WIDTH;
        int boardHeight = cellSize * TetrisGame.HEIGHT;
        int x = boardX - 8;
        int y = boardY + boardHeight / 2 - 48;
        graphics.fill(x, y, x + boardWidth + 16, y + 96, 0xE8090705);
        fillBorder(graphics, x, y, boardWidth + 16, 96, 1, GOLD);
        drawCentered(graphics, title, panelX + panelWidth / 2, y + 22, PALE_GOLD);
        drawCentered(graphics, line1, panelX + panelWidth / 2, y + 47, 0xFFE3D6C1);
        drawCentered(graphics, line2, panelX + panelWidth / 2, y + 68, 0xFFA99A83);
    }

    private void drawControls(GuiGraphics graphics, int buttonY) {
        int left = panelX + 22;
        int right = panelX + panelWidth - 22;
        int top = buttonY - 45;
        int center = panelX + panelWidth / 2;

        graphics.fill(left, top, right, buttonY - 7, 0x55251B10);
        graphics.fill(left + 8, top, right - 8, top + 1, 0x557C5D31);

        drawCentered(graphics, "← / →  ДВИЖЕНИЕ      ↑  ПОВОРОТ",
            center, top + 7, 0xFFB6A17D);
        drawCentered(graphics, "↓  МЯГКИЙ СБРОС      SPACE  СБРОСИТЬ",
            center, top + 21, 0xFFB6A17D);
    }

    private void drawCorner(GuiGraphics graphics, boolean left, boolean top) {
        int cornerX = left ? panelX + 13 : panelX + panelWidth - 13;
        int cornerY = top ? panelY + 13 : panelY + panelHeight - 13;
        int length = 23;

        int horizontalLeft = left ? cornerX : cornerX - length;
        int horizontalRight = left ? cornerX + length : cornerX;
        int horizontalY = top ? cornerY : cornerY - 2;
        graphics.fill(horizontalLeft, horizontalY, horizontalRight, horizontalY + 2, PALE_GOLD);

        int verticalX = left ? cornerX : cornerX - 2;
        int verticalTop = top ? cornerY : cornerY - length;
        graphics.fill(verticalX, verticalTop, verticalX + 2, verticalTop + length, PALE_GOLD);

        // A shorter, thinner inner stroke mirrors the same corner direction.
        int innerX = left ? cornerX + 5 : cornerX - 5;
        int innerY = top ? cornerY + 5 : cornerY - 5;
        int innerLength = 14;
        int innerLeft = left ? innerX : innerX - innerLength;
        int innerRight = left ? innerX + innerLength : innerX;
        int innerHorizontalY = top ? innerY : innerY - 1;
        graphics.fill(innerLeft, innerHorizontalY, innerRight, innerHorizontalY + 1, MUTED_GOLD);

        int innerVerticalX = left ? innerX : innerX - 1;
        int innerTop = top ? innerY : innerY - innerLength;
        graphics.fill(innerVerticalX, innerTop, innerVerticalX + 1, innerTop + innerLength, MUTED_GOLD);
    }

    private void drawTitle(GuiGraphics graphics) {
        int center = panelX + panelWidth / 2;
        drawCentered(graphics, "Tetris by Vitaly_Sokolov", center, panelY + 25, PALE_GOLD);
        graphics.fill(panelX + 35, panelY + 48, panelX + panelWidth - 35, panelY + 49, MUTED_GOLD);
    }

    private void drawStat(GuiGraphics graphics, String label, String value, int x, int y) {
        drawCentered(graphics, label, x, y, 0xFF9B896A);
        drawCentered(graphics, value, x, y + 15, 0xFFFFE7B7);
    }

    private void drawTetrominoEmblem(GuiGraphics graphics, int center, int centerY) {
        int size = Math.max(15, Math.min(25, panelWidth / 19));
        int[][] points = {{0, 0}, {1, 0}, {2, 0}, {1, 1}, {1, 2}};
        for (int[] point : points) {
            drawMaterial(graphics, (point[0] + point[1] * 2) % MATERIALS.length,
                center - size * 2 + point[0] * size, centerY - size + point[1] * size, size);
        }
    }

    private void drawMaterial(GuiGraphics graphics, int materialId, int x, int y, int size) {
        int index = Math.floorMod(materialId, MATERIALS.length);
        try {
            if (spriteCache[index] == null) {
                spriteCache[index] = Minecraft.getInstance().getBlockRenderer()
                    .getBlockModelShaper().getParticleIcon(MATERIALS[index].defaultBlockState());
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteCache[index],
                x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2));
            graphics.fill(x, y, x + size, y + 1, 0xD5E3C17D);
            graphics.fill(x, y + size - 1, x + size, y + size, 0xD5E3C17D);
            graphics.fill(x, y, x + 1, y + size, 0xD5E3C17D);
            graphics.fill(x + size - 1, y, x + size, y + size, 0xD5E3C17D);
        } catch (RuntimeException exception) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF9A7A42);
        }
    }

    private void addButton(GuiGraphics graphics, String id, String label, int x, int y,
                           int buttonWidth, int buttonHeight, int mouseX, int mouseY) {
        buttons.add(new UiButton(id, x, y, buttonWidth, buttonHeight));
        boolean hovered = mouseX >= x && mouseX <= x + buttonWidth
            && mouseY >= y && mouseY <= y + buttonHeight;
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, GOLD);
        graphics.fill(x + 1, y + 1, x + buttonWidth - 1, y + buttonHeight - 1,
            hovered ? 0xFF49381D : 0xFF211A11);
        drawCentered(graphics, label, x + buttonWidth / 2,
            y + (buttonHeight - 8) / 2, hovered ? PALE_GOLD : 0xFFE1C58D);
    }

    private static void fillBorder(GuiGraphics graphics, int x, int y, int borderWidth,
                                   int borderHeight, int thickness, int color) {
        graphics.fill(x, y, x + borderWidth, y + thickness, color);
        graphics.fill(x, y + borderHeight - thickness, x + borderWidth, y + borderHeight, color);
        graphics.fill(x, y, x + thickness, y + borderHeight, color);
        graphics.fill(x + borderWidth - thickness, y, x + borderWidth, y + borderHeight, color);
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int error = dx - dy;
        while (true) {
            graphics.fill(x1, y1, x1 + 2, y1 + 2, color);
            if (x1 == x2 && y1 == y2) break;
            int twice = error * 2;
            if (twice > -dy) { error -= dy; x1 += sx; }
            if (twice < dx) { error += dx; y1 += sy; }
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    private String formatScore(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        double mouseX = event.x();
        double mouseY = event.y();

        for (UiButton uiButton : List.copyOf(buttons)) {
            if (uiButton.contains(mouseX, mouseY)) {
                handleButton(uiButton.id());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleButton(String id) {
        if (confirmReset) {
            if (id.equals("confirm_reset")) {
                confirmReset = false;
                startNewGame();
            } else if (id.equals("cancel_reset")) {
                confirmReset = false;
            }
            return;
        }

        switch (id) {
            case "start" -> startNewGame();
            case "resume" -> {
                if (game.isGameOver()) startNewGame();
                else {
                    game.resume();
                    view = View.GAME;
                    storage.save(game);
                }
            }
            case "pause" -> {
                game.pause();
                storage.save(game);
            }
            case "new" -> confirmReset = true;
            case "records" -> {
                game.pause();
                storage.save(game);
                view = View.RECORDS;
            }
            case "back" -> view = game.hasStarted() ? View.GAME : View.MENU;
            default -> { }
        }
    }

    private void startNewGame() {
        game.newGame();
        view = View.GAME;
        storage.save(game);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (confirmReset) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_Y) {
                confirmReset = false;
                startNewGame();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_N) {
                confirmReset = false;
                return true;
            }
            return true;
        }
        if (view == View.RECORDS) return super.keyPressed(event);

        if (game.hasStarted() && !game.isPaused() && !game.isGameOver()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> game.moveLeft();
                case GLFW.GLFW_KEY_RIGHT -> game.moveRight();
                case GLFW.GLFW_KEY_UP -> game.rotateClockwise();
                case GLFW.GLFW_KEY_DOWN -> game.softDrop();
                case GLFW.GLFW_KEY_SPACE -> game.hardDrop();
                case GLFW.GLFW_KEY_P -> {
                    game.pause();
                    storage.save(game);
                }
                default -> { return super.keyPressed(event); }
            }
            return true;
        }

        if (game.hasStarted() && game.isPaused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                if (game.isGameOver()) startNewGame();
                else game.resume();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_N) {
                confirmReset = true;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        game.pause();
        storage.save(game);
        super.onClose();
    }

    @Override
    public void removed() {
        game.pause();
        storage.save(game);
        super.removed();
    }
}
