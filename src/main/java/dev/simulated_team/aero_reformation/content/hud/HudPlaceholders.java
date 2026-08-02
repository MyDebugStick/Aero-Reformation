package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.network.HudNbtSyncPacket;
import net.minecraft.client.Minecraft;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder system: entry text can embed %xxx% tokens that are resolved at
 * render time into live values (real-world time/date, FPS, player state, world
 * time, ...). Any feature can register additional providers.
 */
public final class HudPlaceholders {

    @FunctionalInterface
    public interface Provider {
        String get(Minecraft mc);
    }

    private static final Map<String, Provider> PROVIDERS = new HashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9_]+)%");
    /** Names currently being resolved (math placeholders), to break circular references. */
    private static final ThreadLocal<Set<String>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    /** Chinese description of a placeholder (built-in or custom), or null. */
    public static String description(String key, Minecraft mc) {
        String d = DESCRIPTIONS.get(key);
        if (d != null) return d;
        if (mc != null && mc.player != null) {
            for (HudPlaceholder p : HudPlaceholderBoard.getPlaceholders(mc.player)) {
                if (p.name.equals(key) && !p.desc.isBlank()) return p.desc;
            }
        }
        return null;
    }

    private HudPlaceholders() {}

    /** Register a placeholder provider (e.g. "real_time" -> clock value). */
    public static void register(String key, Provider provider) {
        PROVIDERS.put(key, provider);
    }

    /** All registered placeholder keys (for UI lists). */
    public static List<String> keys() {
        return new ArrayList<>(PROVIDERS.keySet());
    }

    /** All placeholder keys for the given player: built-ins + custom placeholders. */
    public static List<String> keys(Minecraft mc) {
        List<String> list = new ArrayList<>(PROVIDERS.keySet());
        if (mc.player != null) {
            for (HudPlaceholder p : HudPlaceholderBoard.getPlaceholders(mc.player)) {
                list.add(p.name);
            }
        }
        return list;
    }

    /**
     * Replace every %token% with its live value. Custom placeholders resolve to
     * their bound block's live NBT value. After a placeholder a math suffix is
     * supported, e.g. "%alt%+10" or "%alt%*2", where x is the placeholder value.
     * <p>
     * Conditional blocks are supported: {if EXPR OP EXPR, return A, else return B}.
     * The condition operands are placeholder+math expressions or plain numbers,
     * A/B may be placeholder+math, numbers or literal text (nested {if} allowed).
     */
    public static String resolve(String text, Minecraft mc) {
        if (text == null) return text;
        return resolveInternal(text, mc);
    }

    private static String resolveInternal(String text, Minecraft mc) {
        if (text == null || (text.indexOf('%') < 0 && text.indexOf("{if") < 0)) return text;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '%') {
                int end = text.indexOf('%', i + 1);
                if (end > 0) {
                    String key = text.substring(i + 1, end);
                    String value = lookup(key, mc);
                    int pos = end + 1;
                    ArithSuffix ar = tryArithSuffix(value, text, pos);
                    if (ar != null) {
                        sb.append(ar.result);
                        i = ar.next;
                    } else {
                        sb.append(value);
                        i = pos;
                    }
                    continue;
                }
            } else if (c == '{' && text.startsWith("{if", i)) {
                int end = findMatchingBrace(text, i);
                if (end > 0) {
                    String inner = text.substring(i + 3, end); // between "{if" and "}"
                    String result = evalIfBlock(inner, mc);
                    int pos = end + 1;
                    // The block result may be numeric and followed by a math suffix
                    ArithSuffix ar = tryArithSuffix(result, text, pos);
                    if (ar != null) {
                        sb.append(ar.result);
                        i = ar.next;
                    } else {
                        sb.append(result);
                        i = pos;
                    }
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Index of the '}' matching the '{' at start (nested braces counted), or -1. */
    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Evaluate an "{if ...}" block body: comma-separated branches, each either
     *  "if COND return VAL" or "else return VAL". The leftmost matching branch
     *  wins; a condition may be "c1, c2" (all must hold, AND). The first branch
     *  has no "if" keyword (the outer "{if" already implies it). */
    private static String evalIfBlock(String inner, Minecraft mc) {
        for (String branch : splitTopLevel(inner)) {
            String t = branch.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("else")) {
                return resolveInternal(stripReturn(t.substring("else".length())), mc);
            }
            String body = t.startsWith("if") ? t.substring("if".length()).trim() : t;
            String result = evalIfBranch(body, mc);
            if (result != null) return result;
        }
        return "";
    }

    /** Evaluate one "COND return VAL" branch; null when the condition fails. */
    private static String evalIfBranch(String body, Minecraft mc) {
        String condsPart, resultPart;
        if (body.startsWith("(")) {
            int end = findMatchingParen(body, 0);
            if (end < 0) return null;
            condsPart = body.substring(1, end);
            resultPart = body.substring(end + 1);
        } else {
            int idx = body.indexOf("return");
            if (idx < 0) return null;
            condsPart = body.substring(0, idx);
            resultPart = body.substring(idx);
        }
        // All comma-separated conditions must hold (AND)
        for (String cond : splitTopLevel(condsPart)) {
            if (!evalCondition(cond.trim(), mc)) return null;
        }
        return resolveInternal(stripReturn(resultPart), mc);
    }

    /** Strip an optional leading comma and the "return" keyword. */
    private static String stripReturn(String s) {
        String t = s.trim();
        if (t.startsWith(",")) t = t.substring(1).trim();
        if (t.startsWith("return")) t = t.substring("return".length()).trim();
        return t;
    }

    /** Index of the ')' matching the '(' at start, or -1. */
    private static int findMatchingParen(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Split by top-level commas (outside parentheses and braces). */
    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '{') depth++;
            else if (c == ')' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** Compare two placeholder/constant expressions and decide the branch. */
    private static boolean evalCondition(String cond, Minecraft mc) {
        String[] ops = {"==", "!=", ">=", "<=", "=", ">", "<"};
        int bestPos = -1;
        String bestOp = null;
        for (String op : ops) {
            int p = cond.indexOf(op);
            if (p >= 0 && (bestPos < 0 || p < bestPos)) {
                bestPos = p;
                bestOp = op;
            }
        }
        if (bestPos < 0) return false;
        double l = evalExprValue(cond.substring(0, bestPos), mc);
        double r = evalExprValue(cond.substring(bestPos + bestOp.length()), mc);
        return switch (bestOp) {
            case "=", "==" -> Math.abs(l - r) < 1e-6;
            case "!=" -> Math.abs(l - r) >= 1e-6;
            case ">" -> l > r;
            case "<" -> l < r;
            case ">=" -> l >= r;
            case "<=" -> l <= r;
            default -> false;
        };
    }

    /** Numeric value of an operand: placeholders resolve first, then pure math or first number. */
    private static double evalExprValue(String expr, Minecraft mc) {
        String resolved = resolve(expr, mc);
        Double r = evalMath(0, resolved);
        if (r != null && Double.isFinite(r)) return r;
        Double n = extractFirstNumber(resolved);
        return n != null ? n : 0;
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d*\\.?\\d+");

    private static Double extractFirstNumber(String s) {
        if (s == null) return null;
        Matcher m = NUMBER_PATTERN.matcher(s);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String lookup(String key, Minecraft mc) {
        Provider p = PROVIDERS.get(key);
        if (p != null) return p.get(mc);
        return customValue(key, mc);
    }

    /**
     * Resolve a custom placeholder's live value. Returns the value for
     * "constant" / "math" placeholders, or null when the placeholder is an NBT
     * or sensor bind (those are handled by the caller's normal path).
     */
    public static String resolvePlaceholder(HudPlaceholder ph, Minecraft mc) {
        if ("constant".equals(ph.bindSource)) {
            if (ph.value.isBlank()) return "?";
            if (!ph.math.isBlank()) {
                Double num = tryParse(ph.value.trim());
                if (num != null) {
                    Double r = evalMath(num, ph.math);
                    if (r != null && Double.isFinite(r)) return HudBindings.formatNumber(r);
                }
            }
            return ph.value;
        }
        if ("math".equals(ph.bindSource)) {
            if (ph.value.isBlank()) return "?";
            // Break circular references between math placeholders (A -> B -> A)
            Set<String> resolving = RESOLVING.get();
            if (!resolving.add(ph.name)) return "?";
            try {
                String resolved = resolve(ph.value, mc);
                if (resolved == null || resolved.isBlank()) return "?";
                Double r = evalMath(0, resolved);
                if (r != null && Double.isFinite(r)) return HudBindings.formatNumber(r);
                return resolved; // non-numeric result (plain text)
            } finally {
                resolving.remove(ph.name);
            }
        }
        return null;
    }

    /** Resolve a player-defined placeholder to its bound value (+ math). */
    private static String customValue(String key, Minecraft mc) {
        if (mc.player == null) return "%" + key + "%";
        for (HudPlaceholder ph : HudPlaceholderBoard.getPlaceholders(mc.player)) {
            if (ph.name.equals(key)) {
                // Fixed constant / placeholder arithmetic
                if ("constant".equals(ph.bindSource) || "math".equals(ph.bindSource)) {
                    return resolvePlaceholder(ph, mc);
                }
                // Live sensor data (not stored in NBT)
                if ("sensor".equals(ph.bindSource)) {
                    if (ph.pos == null || ph.sensorType.isEmpty()) return "?";
                    int[] data = dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket.CLIENT_DATA.get(ph.pos);
                    if (data == null) return "?";
                    String sv = HudBindings.formatSensor(ph.sensorType, data);
                    if (!ph.math.isBlank()) {
                        sv = applyMathToFirstNumber(sv, ph.math);
                    }
                    return sv;
                }
                if (ph.nbtPath.isEmpty()) return "?";
                HudNbtSyncPacket.NbtKey nk;
                if (ph.entityUuid != null) {
                    nk = new HudNbtSyncPacket.NbtKey(net.minecraft.core.BlockPos.ZERO, ph.nbtPath, ph.entityUuid);
                } else {
                    if (ph.pos == null) return "?";
                    nk = new HudNbtSyncPacket.NbtKey(ph.pos, ph.nbtPath, null);
                }
                String v = HudNbtSyncPacket.CLIENT_VALUES.get(nk);
                if (v == null) return "?";
                Double num = tryParse(v);
                if (num != null) {
                    if (!ph.math.isBlank()) {
                        Double r = evalMath(num, ph.math);
                        if (r != null && Double.isFinite(r)) return HudBindings.formatNumber(r);
                    }
                    return HudBindings.formatNumber(num);
                }
                return HudBindings.localize(v);
            }
        }
        return "%" + key + "%"; // unknown custom placeholder
    }

    private record ArithSuffix(String result, int next) {}

    /** If the text right after a placeholder starts an arithmetic expression, evaluate it with x = value. */
    private static ArithSuffix tryArithSuffix(String value, String text, int start) {
        Double base = tryParse(value);
        if (base == null) return null;
        // scan the arithmetic portion: digits, '.', operators, parens, x
        int j = start;
        while (j < text.length()) {
            char c = text.charAt(j);
            if (Character.isWhitespace(c) || Character.isDigit(c) || c == '.' || c == '+'
                    || c == '-' || c == '*' || c == '/' || c == '(' || c == ')' || c == 'x' || c == 'X'
                    || c == '^' || c == '√') {
                j++;
            } else {
                break;
            }
        }
        if (j == start) return null; // nothing arithmetic
        String expr = text.substring(start, j);
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) return null;
        char first = trimmed.charAt(0);
        // Only treat it as math when it starts with an operator or '('; otherwise
        // "%xx%米" would wrongly parse the following digits as a standalone value.
        if (first != '+' && first != '-' && first != '*' && first != '/' && first != '('
                && first != '^' && first != '√') {
            return null;
        }
        // evalMath prepends "x" for leading operators, so "+2" -> x+2 (base+2)
        Double r = evalMath(base, trimmed);
        if (r == null || !Double.isFinite(r)) return null;
        return new ArithSuffix(HudBindings.formatNumber(r), j);
    }

    /** Apply a math expression to the first number found in a formatted string (e.g. "100.0 m/s"). */
    private static String applyMathToFirstNumber(String value, String math) {
        if (value == null || value.isEmpty()) return value;
        java.util.regex.Matcher m = NUMBER_PATTERN.matcher(value);
        if (!m.find()) return value;
        double base;
        try {
            base = Double.parseDouble(m.group());
        } catch (NumberFormatException e) {
            return value;
        }
        Double r = evalMath(base, math);
        if (r == null || !Double.isFinite(r)) return value;
        return value.substring(0, m.start()) + HudBindings.formatNumber(r) + value.substring(m.end());
    }

    /** Evaluate a math expression with variable x (used by placeholder math fields). */
    public static Double evalMath(double x, String expr) {
        if (expr == null || expr.isBlank()) return x;
        String s = expr.trim();
        if (s.startsWith("+") || s.startsWith("-") || s.startsWith("*") || s.startsWith("/")) {
            s = "x" + s;
        }
        ArithParser p = new ArithParser(s, 0, x);
        try {
            return p.expression();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Tiny recursive-descent arithmetic parser: numbers, x, + - * / and parentheses. */
    private static final class ArithParser {
        final String s;
        int pos;
        final double x;

        ArithParser(String s, int start, double x) {
            this.s = s;
            this.pos = start;
            this.x = x;
        }

        char peek() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        boolean startsArith() {
            char c = peek();
            return c == '+' || c == '-' || c == '*' || c == '/' || c == '(';
        }

        Double parseWhole() {
            try {
                double v = expression();
                return Double.isFinite(v) ? v : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private double expression() {
            double v = term();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; v += term(); }
                else if (c == '-') { pos++; v -= term(); }
                else break;
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; v *= factor(); }
                else if (c == '/') {
                    pos++;
                    double d = factor();
                    if (d == 0) throw new ArithmeticException("div0");
                    v /= d;
                } else break;
            }
            return v;
        }

        private double factor() {
            double base = primary();
            // Power operator: right-associative, higher precedence than * and /
            if (peek() == '^') {
                pos++;
                return Math.pow(base, factor());
            }
            return base;
        }

        private double primary() {
            char c = peek();
            if (c == '+') { pos++; return primary(); }
            if (c == '-') { pos++; return -primary(); }
            if (c == '(') { pos++; double v = expression(); expect(')'); return v; }
            if (c == 'x' || c == 'X') { pos++; return x; }
            // Square root: "√9" or "sqrt(9)"
            if (c == '√') { pos++; return Math.sqrt(primary()); }
            if (c == 's' && s.startsWith("sqrt", pos)) {
                pos += 4;
                expect('(');
                double v = expression();
                expect(')');
                return Math.sqrt(v);
            }
            int st = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (st == pos) throw new IllegalArgumentException("bad token");
            return Double.parseDouble(s.substring(st, pos));
        }

        private void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("expected " + c);
            pos++;
        }
    }

    static {
        DateTimeFormatter hms = DateTimeFormatter.ofPattern("HH:mm:ss");
        // Real-world time / date (from the local Java clock, no network needed)
        register("real_time", mc -> LocalTime.now().format(hms));
        DESCRIPTIONS.put("real_time", "现实时间（本地时钟，HH:mm:ss）");
        register("real_date", mc -> LocalDate.now().toString());
        DESCRIPTIONS.put("real_date", "现实日期（本地时钟）");
        // Client performance
        register("fps", mc -> String.valueOf(mc.getFps()));
        DESCRIPTIONS.put("fps", "游戏帧率（FPS）");
        // Player state
        register("player", mc -> mc.player != null ? mc.player.getScoreboardName() : "?");
        DESCRIPTIONS.put("player", "玩家名称");
        register("x", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getX()) : "?");
        DESCRIPTIONS.put("x", "玩家 X 坐标");
        register("y", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getY()) : "?");
        DESCRIPTIONS.put("y", "玩家 Y 坐标（高度）");
        register("z", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getZ()) : "?");
        DESCRIPTIONS.put("z", "玩家 Z 坐标");
        register("health", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getHealth()) : "?");
        DESCRIPTIONS.put("health", "玩家生命值");
        register("dimension", mc -> mc.player != null && mc.player.level() != null
                ? mc.player.level().dimension().location().toString() : "?");
        DESCRIPTIONS.put("dimension", "所在维度（如 minecraft:overworld）");
        register("facing", mc -> mc.player != null ? mc.player.getDirection().getName() : "?");
        DESCRIPTIONS.put("facing", "玩家朝向（north/south/west/east）");
        // World time (tick of day, 0..24000)
        register("game_time", mc -> mc.level != null
                ? String.valueOf(mc.level.getDayTime() % 24000L) : "?");
        DESCRIPTIONS.put("game_time", "世界时间（0~24000 tick）");
        // Player coordinates / rotation
        register("block_x", mc -> mc.player != null ? String.valueOf(mc.player.blockPosition().getX()) : "?");
        DESCRIPTIONS.put("block_x", "玩家所在方块 X 坐标（取整）");
        register("block_y", mc -> mc.player != null ? String.valueOf(mc.player.blockPosition().getY()) : "?");
        DESCRIPTIONS.put("block_y", "玩家所在方块 Y 坐标（取整）");
        register("block_z", mc -> mc.player != null ? String.valueOf(mc.player.blockPosition().getZ()) : "?");
        DESCRIPTIONS.put("block_z", "玩家所在方块 Z 坐标（取整）");
        register("yaw", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getYRot()) : "?");
        DESCRIPTIONS.put("yaw", "玩家水平朝向角（度）");
        register("pitch", mc -> mc.player != null ? HudBindings.formatNumber(mc.player.getXRot()) : "?");
        DESCRIPTIONS.put("pitch", "玩家俯仰角（度）");
        register("speed", mc -> {
            if (mc.player == null) return "?";
            double h = Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
            return HudBindings.formatNumber(h * 20.0); // blocks per tick -> m/s
        });
        DESCRIPTIONS.put("speed", "玩家水平速度（m/s）");
        // Player stats
        register("xp", mc -> mc.player != null ? String.valueOf(mc.player.experienceLevel) : "?");
        DESCRIPTIONS.put("xp", "玩家经验等级");
        register("xp_progress", mc -> mc.player != null
                ? HudBindings.formatNumber(mc.player.experienceProgress * 100.0) : "?");
        DESCRIPTIONS.put("xp_progress", "经验进度（百分比）");
        register("food", mc -> mc.player != null ? String.valueOf(mc.player.getFoodData().getFoodLevel()) : "?");
        DESCRIPTIONS.put("food", "玩家饥饿值");
        register("saturation", mc -> mc.player != null
                ? HudBindings.formatNumber(mc.player.getFoodData().getSaturationLevel()) : "?");
        DESCRIPTIONS.put("saturation", "玩家饱和度");
        register("air", mc -> mc.player != null ? String.valueOf(mc.player.getAirSupply()) : "?");
        DESCRIPTIONS.put("air", "玩家氧气值（满 300）");
        register("armor", mc -> mc.player != null ? String.valueOf(mc.player.getArmorValue()) : "?");
        DESCRIPTIONS.put("armor", "玩家护甲值");
        // World / environment
        register("day", mc -> mc.level != null ? String.valueOf(mc.level.getDayTime() / 24000L + 1) : "?");
        DESCRIPTIONS.put("day", "世界天数");
        register("chunk_x", mc -> mc.player != null ? String.valueOf(mc.player.chunkPosition().x) : "?");
        DESCRIPTIONS.put("chunk_x", "玩家所在区块 X 坐标");
        register("chunk_z", mc -> mc.player != null ? String.valueOf(mc.player.chunkPosition().z) : "?");
        DESCRIPTIONS.put("chunk_z", "玩家所在区块 Z 坐标");
        register("biome", mc -> mc.player != null && mc.player.level() != null
                ? mc.player.level().getBiome(mc.player.blockPosition()).getRegisteredName() : "?");
        DESCRIPTIONS.put("biome", "玩家所在生物群系（如 minecraft:plains）");
        register("light", mc -> mc.player != null && mc.player.level() != null
                ? String.valueOf(mc.player.level().getMaxLocalRawBrightness(mc.player.blockPosition())) : "?");
        DESCRIPTIONS.put("light", "玩家所在方块光照等级");
        register("held_item", mc -> mc.player != null
                ? (mc.player.getMainHandItem().isEmpty() ? "空手"
                : mc.player.getMainHandItem().getHoverName().getString()) : "?");
        DESCRIPTIONS.put("held_item", "玩家手持物品名（空手显示空手）");
        // Movement / state booleans (for {if} conditions)
        register("on_ground", mc -> mc.player != null ? String.valueOf(mc.player.onGround()) : "?");
        DESCRIPTIONS.put("on_ground", "是否着地（true/false）");
        register("flying", mc -> mc.player != null ? String.valueOf(mc.player.getAbilities().flying) : "?");
        DESCRIPTIONS.put("flying", "是否在飞行（true/false）");
        register("sneaking", mc -> mc.player != null ? String.valueOf(mc.player.isShiftKeyDown()) : "?");
        DESCRIPTIONS.put("sneaking", "是否潜行（true/false）");
        register("sprinting", mc -> mc.player != null ? String.valueOf(mc.player.isSprinting()) : "?");
        DESCRIPTIONS.put("sprinting", "是否疾跑（true/false）");
    }
}
