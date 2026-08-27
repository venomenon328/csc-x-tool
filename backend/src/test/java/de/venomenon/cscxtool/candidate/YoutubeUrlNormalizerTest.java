package de.venomenon.cscxtool.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import org.junit.jupiter.api.Test;

class YoutubeUrlNormalizerTest {

    private final YoutubeUrlNormalizer normalizer = new YoutubeUrlNormalizer();

    @Test
    void normalizesAllSupportedVideoFormsAndRetainsUsefulStartTimes() {
        assertThat(normalizer.normalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=ignored&t=1m30s"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1m30s");
        assertThat(normalizer.normalize("https://m.youtube.com/watch?v=dQw4w9WgXcQ&start=90"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90");
        assertThat(normalizer.normalize("http://youtu.be/dQw4w9WgXcQ?t=42"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42");
        assertThat(normalizer.normalize("https://youtube.com/shorts/dQw4w9WgXcQ"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(normalizer.normalize("https://music.youtube.com/live/dQw4w9WgXcQ"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(normalizer.normalize("https://youtube.com/embed/dQw4w9WgXcQ"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void rejectsForeignAndPlaylistOnlyUrls() {
        assertThatThrownBy(() -> normalizer.normalize("https://example.com/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("gültigen YouTube");
        assertThatThrownBy(() -> normalizer.normalize("https://youtube.com/playlist?list=PL123"))
                .isInstanceOf(ApiBadRequestException.class);
        assertThatThrownBy(() -> normalizer.normalize("https://youtube.com/watch?list=PL123"))
                .isInstanceOf(ApiBadRequestException.class);
    }
}
