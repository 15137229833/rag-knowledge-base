package com.rag.kb.service;

import com.rag.kb.config.MultimodalProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFrameExtractorService {

    private final MultimodalProperties multimodalProperties;

    public List<OllamaVisionCaptionService.VideoFrame> extractFrames(byte[] videoBytes, String filename) {
        if (videoBytes == null || videoBytes.length == 0) {
            return List.of();
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rag-video-frames-");
            Path input = tempDir.resolve("input" + extensionOf(filename));
            Files.write(input, videoBytes);

            String framePattern = tempDir.resolve("frame-%03d.jpg").toString();
            int maxFrames = Math.max(1, multimodalProperties.getVideoMaxFrames());
            int width = multimodalProperties.getVideoFrameWidth();
            String filter = width > 0 ? "fps=1/3,scale='min(iw," + width + ")':-2" : "fps=1/3";

            Process process = new ProcessBuilder(
                            multimodalProperties.getFfmpegCommand(),
                            "-y",
                            "-i",
                            input.toString(),
                            "-vf",
                            filter,
                            "-frames:v",
                            String.valueOf(maxFrames),
                            framePattern)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("ffmpeg 执行失败: " + trim(output, 500));
            }

            List<Path> frameFiles = new ArrayList<>();
            try (var stream = Files.list(tempDir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("frame-"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(frameFiles::add);
            }

            List<OllamaVisionCaptionService.VideoFrame> frames = new ArrayList<>();
            for (Path frameFile : frameFiles) {
                frames.add(new OllamaVisionCaptionService.VideoFrame(
                        Files.readAllBytes(frameFile), frameFile.getFileName().toString()));
            }
            return frames;
        } catch (Exception e) {
            throw new IllegalStateException("视频抽帧失败: " + e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private static String extensionOf(String filename) {
        String name = filename == null ? "video.mp4" : filename;
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return ".mp4";
        }
        return name.substring(idx).toLowerCase(Locale.ROOT);
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
