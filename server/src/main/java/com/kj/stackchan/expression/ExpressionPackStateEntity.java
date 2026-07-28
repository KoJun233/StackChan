package com.kj.stackchan.expression;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(ExpressionPackStateId.class)
@Table(name = "expression_pack_states")
public class ExpressionPackStateEntity {

    @Id
    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Id
    @Column(name = "state_name", nullable = false, length = 32)
    private String stateName;

    @Column(name = "image_sha256", nullable = false, length = 64)
    private String imageSha256;

    @Column(name = "image_size", nullable = false)
    private int imageSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    protected ExpressionPackStateEntity() {
    }

    ExpressionPackStateEntity(UUID packId, ExpressionState state, byte[] image, String sha256) {
        this.packId = packId;
        this.stateName = state.wireName();
        this.imageSha256 = sha256;
        this.imageData = image.clone();
        this.imageSize = image.length;
    }

    public UUID getPackId() { return packId; }
    public String getStateName() { return stateName; }
    public String getImageSha256() { return imageSha256; }
    public int getImageSize() { return imageSize; }
    public byte[] getImageData() { return imageData.clone(); }
}
