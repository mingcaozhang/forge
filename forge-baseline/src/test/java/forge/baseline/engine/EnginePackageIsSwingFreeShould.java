package forge.baseline.engine;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the 28.2 rule that the adapter runs non-interactively: no Swing, no
 * GUI layer, and no latch-blocking input classes in the engine package's main
 * sources. The kept delta protocol ({@code forge.gamemodes.net}) is allowed;
 * the CMatchUI path stays untouched.
 */
public class EnginePackageIsSwingFreeShould {

    @Test
    public void mainEngineSourcesStayHeadless() throws IOException {
        Path packageDir = Paths.get("src", "main", "java", "forge", "baseline", "engine");
        assertThat(packageDir).as("engine package directory (run from the module base dir)").isDirectory();

        List<String> offenders = Files.list(packageDir)
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(p -> {
                    try {
                        return Files.readAllLines(p).stream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .filter(line -> line.startsWith("import "))
                .filter(line -> line.contains("java.awt")
                        || line.contains("javax.swing")
                        || line.contains("forge.gui")
                        || line.contains("forge.download")
                        || line.contains("forge.screens")
                        || line.contains("forge.gamemodes.match.input"))
                .collect(Collectors.toList());

        assertThat(offenders)
                .as("the engine package must stay free of Swing/GUI/input imports")
                .isEmpty();
    }
}
