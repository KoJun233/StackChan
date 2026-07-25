package com.kj.stackchan.wakeword;

import java.nio.file.Files;
import java.nio.file.Path;

import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EspSrWakeWordModelCatalogTest {

    private static final String XIAOFENG = "wn9_xiao3feng1xiao3feng1_tts3";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void packagesXiaofengWithTheFactoryFallback() throws Exception {
        writeModel(XIAOFENG);
        writeModel(WakeWordModelPackageValidator.DEFAULT_MODEL_NAME);
        AppProperties properties = new AppProperties();
        properties.setWakeModelCatalogDirectory(temporaryDirectory.toString());
        EspSrWakeWordModelCatalog catalog = new EspSrWakeWordModelCatalog(
                properties, new WakeWordModelPackageValidator());

        GeneratedWakeWordModel model = catalog.packageModel(XIAOFENG);

        assertThat(model.modelName()).isEqualTo(XIAOFENG);
        assertThat(model.artifact()).hasSizeGreaterThan(0);
        assertThat(catalog.options()).extracting(WakeWordModelOption::phrase)
                .contains("Hi, Stack Chan", "小峰小峰");
    }

    @Test
    void rejectsNamesOutsideTheFixedCatalog() {
        AppProperties properties = new AppProperties();
        properties.setWakeModelCatalogDirectory(temporaryDirectory.toString());
        EspSrWakeWordModelCatalog catalog = new EspSrWakeWordModelCatalog(
                properties, new WakeWordModelPackageValidator());

        assertThatThrownBy(() -> catalog.packageModel("wn9_arbitrary"))
                .isInstanceOf(InvalidWakeWordModelJobException.class);
    }

    @Test
    void packagesEveryModelFromThePinnedEspSrCatalog() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path modelDirectory = Files.isDirectory(workingDirectory.resolve("wakenet-models"))
                ? workingDirectory.resolve("wakenet-models")
                : workingDirectory.resolve("server/wakenet-models");
        AppProperties properties = new AppProperties();
        properties.setWakeModelCatalogDirectory(modelDirectory.toString());
        EspSrWakeWordModelCatalog catalog = new EspSrWakeWordModelCatalog(
                properties, new WakeWordModelPackageValidator());

        assertThat(catalog.options()).allSatisfy(option -> {
            GeneratedWakeWordModel model = catalog.packageModel(option.modelName());
            assertThat(model.artifact().length).isBetween(1, WakeWordModelPackageValidator.MAX_ARTIFACT_SIZE);
        });
    }

    private void writeModel(String modelName) throws Exception {
        Path modelDirectory = Files.createDirectory(temporaryDirectory.resolve(modelName));
        Files.writeString(modelDirectory.resolve("_MODEL_INFO_"), "test");
        Files.write(modelDirectory.resolve("wn9_data"), new byte[] {1, 2, 3});
        Files.write(modelDirectory.resolve("wn9_index"), new byte[] {4, 5});
    }
}
