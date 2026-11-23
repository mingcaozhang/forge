package forge.baseline.state;

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
 * Guards the plan rule that the baseline state contract stays UI-agnostic: no
 * forge-gui (network/GUI) or Swing imports in the state package's main sources.
 */
public class StatePackageIsUiFreeShould {

    @Test
    public void mainStateSourcesStayUiFree() throws IOException {
        Path packageDir = Paths.get("src", "main", "java", "forge", "baseline", "state");
        assertThat(packageDir).as("state package directory (run from the module base dir)").isDirectory();

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
                .filter(line -> line.contains("forge.gamemodes")
                        || line.contains("forge.gui")
                        || line.contains("forge.download")
                        || line.contains("java.awt")
                        || line.contains("javax.swing"))
                .collect(Collectors.toList());

        assertThat(offenders)
                .as("the state package must stay free of forge-gui/Swing imports")
                .isEmpty();
    }
}
