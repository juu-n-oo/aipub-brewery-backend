package io.ten1010.imagekitbackend.volume.service;

import io.ten1010.imagekitbackend.volume.dto.FileEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * helper Pod 에서 실행한 {@code ls -lan} 출력을 {@link FileEntry} 목록으로 파싱한다.
 * <p>
 * exec/WebSocket I/O 와 분리된 순수 텍스트 파싱 로직이므로 k8s 의존 없이 단위 테스트가 가능하다.
 * {@code total} 헤더와 {@code .}/{@code ..} 는 제외하고, 디렉토리 우선 → 이름순으로 정렬한다.
 */
final class LsOutputParser {

    private LsOutputParser() {
    }

    static List<FileEntry> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        return output.lines()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("total"))
                .map(LsOutputParser::parseLine)
                .filter(Objects::nonNull)
                .filter(e -> !".".equals(e.getName()) && !"..".equals(e.getName()))
                .sorted(Comparator
                        .comparing((FileEntry e) -> e.getType() == FileEntry.FileType.FILE ? 1 : 0)
                        .thenComparing(FileEntry::getName))
                .toList();
    }

    private static FileEntry parseLine(String line) {
        String[] parts = line.trim().split("\\s+", 9);
        if (parts.length < 9) {
            return null;
        }

        String permissions = parts[0];
        FileEntry.FileType type = permissions.startsWith("d") ? FileEntry.FileType.DIRECTORY : FileEntry.FileType.FILE;
        Long size = type == FileEntry.FileType.FILE ? parseLong(parts[4]) : null;
        String modifiedAt = parts[5] + " " + parts[6] + " " + parts[7];
        String name = parts[8];

        return FileEntry.builder()
                .name(name)
                .type(type)
                .size(size)
                .modifiedAt(modifiedAt)
                .build();
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
