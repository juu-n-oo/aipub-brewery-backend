package io.ten1010.imagekitbackend.dockerfile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "dockerfiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project", "username", "name"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Dockerfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String project;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "base_image", length = 512, nullable = false)
    private String baseImage;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_revision_id")
    private DockerfileRevision latestRevision;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

}
