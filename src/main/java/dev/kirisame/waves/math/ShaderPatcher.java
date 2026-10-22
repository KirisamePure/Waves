package dev.kirisame.waves.math;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small lexical adapter for Iris's already preprocessed, compatibility-style terrain sources. */
public final class ShaderPatcher {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern ENTITY = Pattern.compile("\\b(?:attribute|in)\\s+(float|vec[234])\\s+mc_Entity\\b");
    private static final Pattern TOKENS = Pattern.compile("\\b(gl_Vertex|gl_Normal|ftransform)\\b");

    private ShaderPatcher() {}

    public static String patch(String source, String library) {
        if (source == null) return null;
        String code = maskComments(source);
        // Core-profile/custom attribute packs need a dedicated adapter. Leave them untouched.
        if (code.contains("waves_original_main") || !code.matches("(?s).*\\b(gl_Vertex|ftransform)\\b.*")) return source;
        Matcher main = MAIN.matcher(code);
        if (!main.find()) return source;
        int mainName = code.indexOf("main", main.start());
        source = source.substring(0, mainName) + "waves_original_main" + source.substring(mainName + 4);
        code = maskComments(source);
        Matcher entity = ENTITY.matcher(code);
        boolean declared = entity.find();
        String material = declared && entity.group(1).equals("float") ? "mc_Entity" : "mc_Entity.x";
        if (!declared && Pattern.compile("\\bmc_Entity\\b").matcher(code).find()) return source.replace("waves_original_main", "main");

        Matcher token = TOKENS.matcher(code);
        StringBuilder rewritten = new StringBuilder();
        int end = 0;
        while (token.find()) {
            if (token.start() < end) continue;
            rewritten.append(source, end, token.start());
            switch (token.group()) {
                case "gl_Vertex" -> { rewritten.append("waves_vertex"); end = token.end(); }
                case "gl_Normal" -> { rewritten.append("waves_normal"); end = token.end(); }
                default -> {
                    Matcher call = Pattern.compile("\\s*\\(\\s*\\)").matcher(code);
                    call.region(token.end(), code.length());
                    if (!call.lookingAt()) return source.replace("waves_original_main", "main");
                    rewritten.append("(gl_ModelViewProjectionMatrix * waves_vertex)");
                    end = call.end();
                }
            }
        }
        rewritten.append(source, end, source.length());
        source = rewritten.toString();
        int insertion = preambleEnd(maskComments(source));
        String declarations = "\n" + (declared ? "" : "attribute vec4 mc_Entity;\n")
                + "vec4 waves_vertex = vec4(0.0);\nvec3 waves_normal = vec3(0.0, 1.0, 0.0);\nvarying vec3 waves_replacement_pos;\nvarying float waves_replacement_water;\n" + library + "\n";
        return source.substring(0, insertion) + declarations + source.substring(insertion)
                + "\nvoid main() {\n"
                + "  waves_normal = gl_Normal;\n"
                + "  waves_vertex = waves_displace(gl_Vertex, gl_Normal, " + material + ", waves_normal);\n"
                + "  waves_replacement_pos = gl_Vertex.xyz;\n"
                + "  waves_replacement_water = waves_water_id >= 0.0 && abs(" + material + " - waves_water_id) < 0.25 ? 1.0 : 0.0;\n"
                + "  waves_original_main();\n}\n";
    }

    public static String patchFragment(String source, String library) {
        if (source == null) return null;
        String code = maskComments(source);
        if (code.contains("waves_original_fragment")) return source;
        Matcher main = MAIN.matcher(code);
        if (!main.find()) return source;
        int name = code.indexOf("main", main.start());
        source = source.substring(0, name) + "waves_original_fragment" + source.substring(name + 4);
        int insertion = preambleEnd(maskComments(source));
        String declarations = "\nvarying vec3 waves_replacement_pos;\nvarying float waves_replacement_water;\n" + library + "\n";
        return source.substring(0, insertion) + declarations + source.substring(insertion)
                + "\nvoid main() { if (waves_surface_replace > 0.5 && waves_replacement_water > 0.5 && waves_cell_covered(waves_replacement_pos.xz) > 0.5 && abs(normalize(cross(dFdx(waves_replacement_pos), dFdy(waves_replacement_pos))).y) < 0.1) discard; waves_original_fragment(); }\n";
    }

    private static int preambleEnd(String code) {
        int offset = 0;
        while (offset < code.length()) {
            int newline = code.indexOf('\n', offset);
            int next = newline < 0 ? code.length() : newline + 1;
            String line = code.substring(offset, next).trim();
            if (!line.isEmpty() && !line.startsWith("#")) return offset;
            offset = next;
        }
        return offset;
    }

    // Preserve byte offsets/newlines while excluding comment contents from all token searches.
    public static String maskComments(String source) {
        char[] chars = source.toCharArray();
        boolean block = false, line = false;
        for (int i = 0; i < chars.length; i++) {
            if (line) {
                if (chars[i] == '\n') line = false;
                else chars[i] = ' ';
            } else if (block) {
                if (chars[i] == '*' && i + 1 < chars.length && chars[i + 1] == '/') {
                    chars[i] = chars[++i] = ' '; block = false;
                } else if (chars[i] != '\n' && chars[i] != '\r') chars[i] = ' ';
            } else if (chars[i] == '/' && i + 1 < chars.length) {
                if (chars[i + 1] == '/') { chars[i] = chars[++i] = ' '; line = true; }
                else if (chars[i + 1] == '*') { chars[i] = chars[++i] = ' '; block = true; }
            }
        }
        return new String(chars);
    }
}
