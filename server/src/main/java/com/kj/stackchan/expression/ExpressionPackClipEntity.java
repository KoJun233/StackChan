package com.kj.stackchan.expression;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(ExpressionPackClipId.class)
@Table(name = "expression_pack_clips")
public class ExpressionPackClipEntity {
    @Id @Column(name = "pack_id", nullable = false) private UUID packId;
    @Id @Column(name = "clip_name", nullable = false, length = 32) private String clipName;
    @Column(name = "clip_sha256", nullable = false, length = 64) private String clipSha256;
    @Column(name = "clip_size", nullable = false) private int clipSize;
    @Column(name = "frame_count", nullable = false) private int frameCount;
    @Column(name = "frame_delay_ms", nullable = false) private int frameDelayMs;
    @Column(name = "clip_data", nullable = false) private byte[] clipData;

    protected ExpressionPackClipEntity() {}

    ExpressionPackClipEntity(UUID packId, LifecycleClip clip, GeneratedLifecycleExpressionPack.Clip value) {
        this.packId = packId;
        this.clipName = clip.wireName();
        this.clipSha256 = value.sha256();
        this.clipData = value.data();
        this.clipSize = clipData.length;
        this.frameCount = value.frameCount();
        this.frameDelayMs = value.frameDelayMs();
    }

    public UUID getPackId() { return packId; }
    public String getClipName() { return clipName; }
    public String getClipSha256() { return clipSha256; }
    public int getClipSize() { return clipSize; }
    public int getFrameCount() { return frameCount; }
    public int getFrameDelayMs() { return frameDelayMs; }
    public byte[] getClipData() { return clipData.clone(); }
}
