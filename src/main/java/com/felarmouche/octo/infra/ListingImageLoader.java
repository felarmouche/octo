package com.felarmouche.octo.infra;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class ListingImageLoader {

    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024; // 20 MB Download-Limit (Schutz vor RAM-Überlauf)
    private static final int MAX_DIMENSION = 1280;                  // Max. Breite oder Höhe in Pixeln
    private static final double JPEG_QUALITY = 0.80;                // 80% Qualität (optimaler Kompromiss)

    private final RestClient http;

    public ListingImageLoader(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = builder.requestFactory(factory).build();
    }

    public List<Media> load(List<String> urls, int maxImages) {
        if (urls == null || maxImages == 0) {
            return List.of();
        }
        return urls.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(maxImages)
                .map(this::loadSingle)
                .filter(Objects::nonNull)
                .toList();
    }

    private Media loadSingle(String url) {
        try {
            ResponseEntity<byte[]> response = http.get().uri(url).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();


            if (body == null || body.length == 0 || body.length > MAX_DOWNLOAD_BYTES) {
                log.warn("Bild {} leer oder Download zu groß ({} Bytes)", url, body == null ? 0 : body.length);
                return null;
            }

            byte[] compressedBytes = compressImage(body);

            // Da wir immer in JPEG konvertieren, ist der MimeType garantiert IMAGE_JPEG
            return new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(compressedBytes));

        } catch (RuntimeException | IOException e) {
            log.warn("Bild {} konnte nicht geladen oder komprimiert werden: {}", url, e.getMessage());
            return null;
        }
    }


    private byte[] compressImage(byte[] originalBytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {


            Thumbnails.of(input)
                    .size(MAX_DIMENSION, MAX_DIMENSION) // Behält das Seitenverhältnis automatisch bei!
                    .imageType(BufferedImage.TYPE_INT_RGB) // Verhindert Farb-/Transparenzfehler bei PNG -> JPG
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toOutputStream(output);


            return output.toByteArray();
        }
    }
}