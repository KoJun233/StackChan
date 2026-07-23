package com.kj.stackchan.wakeword;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kj.stackchan.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class EspSrWakeWordModelCatalog {

    private static final int FIXED_NAME_SIZE = 32;
    private static final List<String> REQUIRED_FILES = List.of("_MODEL_INFO_", "wn9_data", "wn9_index");
    private static final List<WakeWordModelOption> OPTIONS = List.of(
            new WakeWordModelOption("wn9l_histackchan_tts3", "Hi, Stack Chan", "en"),
            new WakeWordModelOption("wn9_xiao3feng1xiao3feng1_tts3", "小峰小峰", "zh-CN"),
            new WakeWordModelOption("wn9l_xiaoaitongxue", "小爱同学", "zh-CN"),
            new WakeWordModelOption("wn9l_nihaoxiaozhi_tts3", "你好小智", "zh-CN"),
            new WakeWordModelOption("wn9l_ni3hao3xing1bao3_tts3", "你好星宝", "zh-CN"),
            new WakeWordModelOption("wn9_hilexin", "Hi, 乐鑫", "zh-CN"),
            new WakeWordModelOption("wn9_hiesp", "Hi, ESP", "en"),
            new WakeWordModelOption("wn9_alexa", "Alexa", "en"),
            new WakeWordModelOption("wn9_jarvis_tts", "Jarvis", "en"),
            new WakeWordModelOption("wn9_computer_tts", "Computer", "en"),
            new WakeWordModelOption("wn9l_heygigi", "Hey Gigi", "en"),
            new WakeWordModelOption("wn9l_fr_bonjouresp_tts3", "Bonjour ESP", "fr"),
            new WakeWordModelOption("wn9l_ja_konnichihaesp_tts3", "こんにちは ESP", "ja")
    );

    private final Path catalogDirectory;
    private final WakeWordModelPackageValidator validator;

    public EspSrWakeWordModelCatalog(AppProperties properties, WakeWordModelPackageValidator validator) {
        this.catalogDirectory = Path.of(properties.getWakeModelCatalogDirectory()).toAbsolutePath().normalize();
        this.validator = validator;
    }

    public List<WakeWordModelOption> options() {
        return OPTIONS;
    }

    public WakeWordModelOption requireOption(String modelName) {
        return OPTIONS.stream()
                .filter(option -> option.modelName().equals(modelName))
                .findFirst()
                .orElseThrow(InvalidWakeWordModelJobException::new);
    }

    public GeneratedWakeWordModel packageModel(String modelName) {
        requireOption(modelName);
        List<String> modelNames = new ArrayList<>();
        modelNames.add(modelName);
        if (!WakeWordModelPackageValidator.DEFAULT_MODEL_NAME.equals(modelName)) {
            modelNames.add(WakeWordModelPackageValidator.DEFAULT_MODEL_NAME);
        }
        try {
            byte[] artifact = pack(modelNames);
            return validator.validate(modelName, WakeWordModelPackageValidator.sha256(artifact), artifact);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidWakeWordModelJobException invalidJob) {
                throw invalidJob;
            }
            throw new WakeWordModelCatalogUnavailableException(exception);
        }
    }

    private byte[] pack(List<String> modelNames) throws IOException {
        Map<String, Map<String, byte[]>> models = new LinkedHashMap<>();
        int fileCount = 0;
        long totalFileBytes = 0;
        for (String modelName : modelNames) {
            Path modelDirectory = catalogDirectory.resolve(modelName).normalize();
            if (!modelDirectory.getParent().equals(catalogDirectory) || !Files.isDirectory(modelDirectory) ||
                    Files.isSymbolicLink(modelDirectory)) {
                throw new IOException("built-in wake model is unavailable");
            }
            Map<String, byte[]> files = new LinkedHashMap<>();
            for (String fileName : REQUIRED_FILES) {
                Path file = modelDirectory.resolve(fileName).normalize();
                if (!file.getParent().equals(modelDirectory) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                    throw new IOException("built-in wake model file is unavailable");
                }
                long size = Files.size(file);
                if (size <= 0 || size > WakeWordModelPackageValidator.MAX_ARTIFACT_SIZE) {
                    throw new IOException("built-in wake model file size is invalid");
                }
                totalFileBytes += size;
                if (totalFileBytes > WakeWordModelPackageValidator.MAX_ARTIFACT_SIZE) {
                    throw new IOException("built-in wake model package is too large");
                }
                files.put(fileName, Files.readAllBytes(file));
                fileCount++;
            }
            models.put(modelName, files);
        }

        int headerLength = Integer.BYTES + models.size() * (FIXED_NAME_SIZE + Integer.BYTES) +
                fileCount * (FIXED_NAME_SIZE + Integer.BYTES * 2);
        ByteBuffer header = ByteBuffer.allocate(headerLength).order(ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        header.putInt(models.size());
        for (Map.Entry<String, Map<String, byte[]>> model : models.entrySet()) {
            putFixedName(header, model.getKey());
            header.putInt(model.getValue().size());
            for (Map.Entry<String, byte[]> file : model.getValue().entrySet()) {
                putFixedName(header, file.getKey());
                header.putInt(headerLength + data.size());
                header.putInt(file.getValue().length);
                data.write(file.getValue());
            }
        }
        ByteArrayOutputStream artifact = new ByteArrayOutputStream(headerLength + data.size());
        artifact.write(header.array());
        data.writeTo(artifact);
        return artifact.toByteArray();
    }

    private void putFixedName(ByteBuffer buffer, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length == 0 || bytes.length >= FIXED_NAME_SIZE) {
            throw new IOException("invalid built-in wake model name");
        }
        buffer.put(bytes);
        buffer.put(new byte[FIXED_NAME_SIZE - bytes.length]);
    }
}
