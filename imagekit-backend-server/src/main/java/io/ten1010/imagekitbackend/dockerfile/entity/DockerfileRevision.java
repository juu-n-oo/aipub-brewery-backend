package io.ten1010.imagekitbackend.dockerfile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "dockerfile_revisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dockerfile_id", "version"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DockerfileRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dockerfile_id", nullable = false)
    private Dockerfile dockerfile;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "base_image", length = 512, nullable = false)
    private String baseImage;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

}
