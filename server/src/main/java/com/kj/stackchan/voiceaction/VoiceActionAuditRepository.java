package com.kj.stackchan.voiceaction;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceActionAuditRepository extends JpaRepository<VoiceActionAuditEntity, UUID> { }
