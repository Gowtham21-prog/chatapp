package com.chatapp.moderation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Report {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reporter_user_id")
    private UUID reporterUserId;

    @Column(name = "reporter_anonymous_id", length = 64)
    private String reporterAnonymousId;

    @Column(name = "reported_user_id")
    private UUID reportedUserId;

    @Column(name = "reported_anonymous_id", length = 64)
    private String reportedAnonymousId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportContext context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportReason reason;

    @Column(length = 1000)
    private String details;

    @Column(name = "message_id")
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
