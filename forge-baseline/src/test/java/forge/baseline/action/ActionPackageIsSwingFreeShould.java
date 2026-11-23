package forge.baseline.action;

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
 * Guards the plan rule that the baseline action contract stays headless: no
 * Swing or GUI-layer imports in the action package's main sources. The protocol
 * data types of {@code forge.gamemodes.match} (pure Serializable payloads) are
 * allowed; the GUI interface layer is not.
 */
public class ActionPackageIsSwingFreeShould {

    @Test
    public void mainActionSourcesStaySwingFree() throws IOException {
        Path packageDir = Paths.get("src", "main", "java", "forge", "baseline", "action");
        assertThat(packageDir).as("action package directory (run from the module base dir)").isDirectory();

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
                        || line.contains("forge.screens"))
                .collect(Collectors.toList());

        assertThat(offenders)
                .as("the action package must stay free of Swing/GUI imports")
                .isEmpty();
    }
}
