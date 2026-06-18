package io.ten1010.imagekitbackend.volume.service;

import io.ten1010.imagekitbackend.volume.dto.FileEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LsOutputParser} 의 {@code ls -lan} 출력 파싱을 k8s 의존 없이 검증한다(SRV-10: ls 파싱 취약성 고정).
 * 입력 줄 형식: {@code perm links owner group size month day time name}.
 */
class LsOutputParserTest {

    @Test
    void returnsEmptyListForNullOrBlankOutput() {
        assertThat(LsOutputParser.parse(null)).isEmpty();
        assertThat(LsOutputParser.parse("")).isEmpty();
        assertThat(LsOutputParser.parse("   \n   ")).isEmpty();
    }

    @Test
    void parsesFileAndDirectoryFields() {
        String output = String.join("\n",
                "total 24",
                "drwxr-xr-x 4 0 0 4096 Apr 15 09:30 models",
                "-rw-r--r-- 1 0 0 256 Apr 17 12:00 requirements.txt");

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).hasSize(2);

        FileEntry dir = entries.get(0); // 디렉토리가 먼저 정렬된다
        assertThat(dir.getName()).isEqualTo("models");
        assertThat(dir.getType()).isEqualTo(FileEntry.FileType.DIRECTORY);
        assertThat(dir.getSize()).isNull(); // 디렉토리는 size 없음
        assertThat(dir.getModifiedAt()).isEqualTo("Apr 15 09:30");

        FileEntry file = entries.get(1);
        assertThat(file.getName()).isEqualTo("requirements.txt");
        assertThat(file.getType()).isEqualTo(FileEntry.FileType.FILE);
        assertThat(file.getSize()).isEqualTo(256L);
        assertThat(file.getModifiedAt()).isEqualTo("Apr 17 12:00");
    }

    @Test
    void skipsTotalHeaderAndDotEntries() {
        String output = String.join("\n",
                "total 24",
                "drwxr-xr-x 3 0 0 4096 Jun 18 10:00 .",
                "drwxr-xr-x 5 0 0 4096 Jun 17 09:00 ..",
                "-rw-r--r-- 1 0 0 100 Jun 18 10:00 keep.txt");

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).extracting(FileEntry::getName).containsExactly("keep.txt");
    }

    @Test
    void sortsDirectoriesFirstThenByName() {
        String output = String.join("\n",
                "-rw-r--r-- 1 0 0 10 Jan 1 00:00 b.txt",
                "drwxr-xr-x 2 0 0 4096 Jan 1 00:00 zeta",
                "-rw-r--r-- 1 0 0 20 Jan 1 00:00 a.txt",
                "drwxr-xr-x 2 0 0 4096 Jan 1 00:00 alpha");

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).extracting(FileEntry::getName)
                .containsExactly("alpha", "zeta", "a.txt", "b.txt");
    }

    @Test
    void skipsMalformedLinesWithTooFewColumns() {
        String output = String.join("\n",
                "drwxr-xr-x 4 0 0 4096 models",          // 컬럼 6개뿐 → 무시
                "-rw-r--r-- 1 0 0 256 Apr 17 12:00 ok.txt");

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).extracting(FileEntry::getName).containsExactly("ok.txt");
    }

    @Test
    void preservesFilenamesContainingSpaces() {
        // split 의 limit 9 덕분에 마지막 컬럼(이름)은 공백을 포함해도 잘리지 않는다.
        String output = "-rw-r--r-- 1 0 0 512 Apr 18 08:00 my data file.csv";

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.getName()).isEqualTo("my data file.csv");
            assertThat(e.getType()).isEqualTo(FileEntry.FileType.FILE);
            assertThat(e.getSize()).isEqualTo(512L);
        });
    }

    @Test
    void nonNumericFileSizeBecomesNullWithoutThrowing() {
        String output = "-rw-r--r-- 1 0 0 NaN Apr 17 12:00 weird.bin";

        List<FileEntry> entries = LsOutputParser.parse(output);

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.getName()).isEqualTo("weird.bin");
            assertThat(e.getType()).isEqualTo(FileEntry.FileType.FILE);
            assertThat(e.getSize()).isNull();
        });
    }

}
