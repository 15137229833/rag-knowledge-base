package com.rag.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kb_member")
@IdClass(KbMember.KbMemberId.class)
@Getter
@Setter
@NoArgsConstructor
public class KbMember {

    @Id
    @Column(name = "kb_id")
    private UUID kbId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KbPermission permission = KbPermission.READ;

    public KbMember(UUID kbId, UUID userId, KbPermission permission) {
        this.kbId = kbId;
        this.userId = userId;
        this.permission = permission;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class KbMemberId implements Serializable {
        private UUID kbId;
        private UUID userId;

        public KbMemberId(UUID kbId, UUID userId) {
            this.kbId = kbId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            KbMemberId that = (KbMemberId) o;
            return kbId.equals(that.kbId) && userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(kbId, userId);
        }
    }
}
