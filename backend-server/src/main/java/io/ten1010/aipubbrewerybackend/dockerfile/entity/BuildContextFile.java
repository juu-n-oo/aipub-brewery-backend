package io.ten1010.aipubbrewerybackend.dockerfile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "build_context_files")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BuildContextFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dockerfile_id", nullable = false)
    private Dockerfile dockerfile;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String targetPath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String storagePath;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

}
