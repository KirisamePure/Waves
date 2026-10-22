package dev.kirisame.waves;

import dev.kirisame.waves.config.ConfigStore;
import dev.kirisame.waves.config.WavesConfig;
import dev.kirisame.waves.math.ShaderPatcher;
import dev.kirisame.waves.math.WaveMath;
import dev.kirisame.waves.math.ShoreMath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/** Deterministic, non-graphical checks. The render path is separately exercised in Minecraft. */
public final class CoreTest {
    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }

    public static void main(String[] args) throws Exception {
        check(close(WaveMath.weatherMultiplier(0, 0, 1.6, 5), 1), "clear-weather baseline");
        check(close(WaveMath.weatherMultiplier(1, 0, 1.6, 5), 1.6), "rain target");
        check(close(WaveMath.weatherMultiplier(1, 1, 1.6, 5), 5), "full thunder target");
        check(close(WaveMath.weatherMultiplier(2, 2, 1.6, 5), 5), "weather input bounds");
        double transition = 1;
        boolean monotonic = true;
        for (int i = 0; i < 200; i++) {
            double next = WaveMath.approach(transition, 5, .05, 8);
            monotonic &= next > transition && next < 5;
            transition = next;
        }
        check(monotonic, "200 storm smoothing steps are bounded and monotonic");
        check(close(WaveMath.approach(1, 5, 1, 8), WaveMath.approach(WaveMath.approach(1, 5, .5, 8), 5, .5, 8)), "frame-rate independent smoothing");
        double h = WaveMath.height(15, 27, 3, .32, 24, 35, 1, .65);
        check(close(WaveMath.height(15, 27, 3, 1.6, 24, 35, 1, .65), h * 5), "storm amplitude is five times calm");
        check(close(WaveMath.height(15, 27, 3, 0, 24, 35, 1, .65), 0), "zero amplitude is flat");
        check(close(WaveMath.height(15, 27, 0, .32, 24, 35, 0, .65), WaveMath.height(15, 27, 300, .32, 24, 35, 0, .65)), "zero speed freezes waves");
        double cameraX = 29_999_900, cameraZ = -29_999_900;
        double localX = 3.7, localZ = -17.2;
        for (int i = 0; i < WaveMath.COMPONENTS; i++) {
            double angle = WaveMath.angle(i, 35, .75);
            double k = WaveMath.waveNumber(i, 24);
            double cameraPhase = WaveMath.phase(i, cameraX, cameraZ, 123, 24, 35, 1);
            double localPhase = k * (Math.cos(angle) * localX + Math.sin(angle) * localZ) + cameraPhase;
            double worldPhase = WaveMath.phase(i, cameraX + localX, cameraZ + localZ, 123, 24, 35, 1);
            check(Math.abs(Math.sin(localPhase) - Math.sin(worldPhase)) < 1e-7, "camera-relative world-border continuity " + i);
        }
        double[] phases = new double[WaveMath.COMPONENTS];
        double[] shifted = new double[WaveMath.COMPONENTS];
        for (int i = 0; i < phases.length; i++) { phases[i] = .3 + i * 1.13; shifted[i] = phases[i] + i * WaveMath.TAU; }
        check(close(WaveMath.normalizedHeight(phases, .65, .75), WaveMath.normalizedHeight(shifted, .65, .75)), "warping remains continuous across camera phase wrapping");
        check(Math.abs(WaveMath.height(15, 27, 3, 1, 24, 35, 1, .65, 0) - WaveMath.height(15, 27, 3, 1, 24, 35, 1, .65, 1)) > .01, "irregularity changes the wave shape");
        check(Math.abs(WaveMath.height(15, 27, 3, 1, 24, 35, 1, .65) - WaveMath.height(15 + 24, 27, 3, 1, 24, 35, 1, .65)) > .01, "surface does not repeat at a single dominant wavelength");
        boolean[] reef = new boolean[9 * 9]; reef[4 * 9 + 4] = true;
        float[] shore = ShoreMath.distances(reef, 9);
        check(shore[40] == 0 && shore[41] == 1 && shore[42] == 2, "reef distance follows real obstacle cells");
        check(Math.abs(shore[50] - Math.sqrt(2)) < 1e-6, "diagonal shore distance");
        check(ShoreMath.distances(new boolean[81], 9)[40] == ShoreMath.MAX_DISTANCE, "open ocean has no false shoreline");
        check(close(ShoreMath.attenuation(0, 3), .72) && close(ShoreMath.attenuation(3, 3), 1), "shore keeps moving at the solid boundary and recovers offshore");
        check(ShoreMath.attenuation(1, 3) < ShoreMath.attenuation(2, 3), "shore damping is monotonic");

        check(WaveMath.windStrength(0, 0, .35, 1) == 0, "clear weather has no storm wind");
        check(close(WaveMath.windStrength(1, 0, .35, 1), .35), "rain wind uses configured strength");
        check(close(WaveMath.windStrength(1, 1, .35, 1), 1), "thunder wind reaches storm strength");
        float[] land = new float[25]; java.util.Arrays.fill(land, Float.POSITIVE_INFINITY);
        boolean[] ocean = new boolean[25]; ocean[0] = true;
        land[1] = 63; land[2] = 62; land[3] = 66; land[4] = 62; land[24] = 62;
        var runup = dev.kirisame.waves.math.RunupMath.paths(land, ocean, 5, 62.889f, 8);
        check(runup.distance()[1] == 1 && runup.distance()[2] == 2, "run-up connects land to sea");
        check(runup.barrier()[2] == 63 && runup.barrier()[4] == 66, "run-up remembers intervening high ground");
        check(!Float.isFinite(runup.distance()[24]), "isolated land does not flood");
        check(dev.kirisame.waves.math.RunupMath.coverage(62.9, 62, 63, 2, 4) == 0, "water cannot cross a higher barrier");
        check(dev.kirisame.waves.math.RunupMath.coverage(63.2, 63, 63, 1, 4) > 0, "crest higher than shore wets the surface");
        check(dev.kirisame.waves.math.RunupMath.coverage(64, 63, 63, 4, 4) == 0, "run-up respects configured reach");

        var sampler = new WaveMath.Sampler(123, 1.6, 24, 35, 1, .65, .75);
        check(Math.abs(sampler.height(29_999_900, -29_999_900, .3)
                - WaveMath.height(29_999_900, -29_999_900, 122.7, 1.6, 24, 35, 1, .65, .75)) < 1e-7,
                "batched shore sampler matches original waves at world border");

        String library = Files.readString(Path.of("src/main/resources/assets/waves/shaders/waves.glsl"));
        String simple = "#version 120\n#extension GL_ARB_draw_buffers : enable\n// gl_Vertex main()\nvoid main() { gl_Position = ftransform(); }\n";
        String patched = ShaderPatcher.patch(simple, library);
        check(patched.contains("void waves_original_main()"), "rename main definition");
        check(patched.contains("attribute vec4 mc_Entity;"), "supply missing water attribute");
        check(patched.contains("gl_ModelViewProjectionMatrix * waves_vertex"), "ftransform uses displaced vertex");
        check(patched.indexOf("#extension") < patched.indexOf("uniform float"), "preserve GLSL extension ordering");
        check(patched.contains("// gl_Vertex main()"), "leave comments untouched");
        check(ShaderPatcher.patch(patched, library).equals(patched), "patch is idempotent");
        check(ShaderPatcher.patch(null, library) == null, "missing optional shader is preserved");
        String core = "#version 330 core\nin vec3 position;\nvoid main(){gl_Position=vec4(position,1);}";
        check(ShaderPatcher.patch(core, library).equals(core), "unsupported custom inputs remain unchanged");
        for (String type : new String[]{"float", "vec2", "vec3", "vec4"}) {
            String source = "#version 120\nattribute " + type + " mc_Entity;\nvoid main(void) {gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;}";
            String output = ShaderPatcher.patch(source, library);
            check(output.contains("attribute " + type + " mc_Entity;"), "keep existing entity attribute " + type);
            check(output.contains(type.equals("float") ? "gl_Normal, mc_Entity, waves_normal" : "gl_Normal, mc_Entity.x, waves_normal"), "water ID type " + type);
        }

        WavesConfig config = new WavesConfig();
        config.amplitude = Double.NaN; config.speed = -1; config.wavelength = 0;
        config.rainMultiplier = 4; config.thunderMultiplier = 2;
        config.runupReach = 100; config.windRain = .8; config.windThunder = .2; config.splashHeight = Double.NaN; config.splashSize = 8; config.soundVolume = 2; config.sounds = false;
        config.sanitize();
        check(close(config.amplitude, .32) && config.speed == 0 && config.wavelength == 8, "sanitize nonfinite and out-of-range settings");
        check(config.thunderMultiplier == 4, "storm is never weaker than rain");
        check(config.runupReach == 8 && config.windThunder == .8, "run-up reach and weather wind settings are bounded");
        check(config.splashHeight == 1 && config.splashSize == 3 && config.soundVolume == 1, "sanitize spray size, launch height and surf volume");
        Path directory = Files.createTempDirectory("waves-config-test-");
        Path file = directory.resolve("waves.json");
        ConfigStore store = new ConfigStore(file, LoggerFactory.getLogger("waves-test"));
        check(store.save(config), "save config atomically");
        check(store.load().thunderMultiplier == 4, "config round-trip");
        check(!store.load().sounds && store.load().soundVolume == 1 && store.load().splashHeight == 1 && store.load().splashSize == 3,
                "spray and sound settings survive config round-trip");
        Files.writeString(file, "{not valid json");
        check(store.load().thunderMultiplier == 5, "malformed config recovers defaults");
        check(Files.readString(file).equals("{not valid json"), "malformed original is preserved");
        try (var paths = Files.list(directory)) { check(paths.anyMatch(p -> p.getFileName().toString().startsWith("waves.json.invalid-")), "malformed config backup exists"); }
        System.out.println("Waves core checks passed: " + checks);
    }
}
