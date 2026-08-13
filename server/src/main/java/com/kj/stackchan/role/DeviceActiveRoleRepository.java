package com.kj.stackchan.role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeviceActiveRoleRepository extends JpaRepository<DeviceActiveRoleEntity, UUID> {
    Optional<DeviceActiveRoleEntity> findByDeviceId(UUID deviceId);
    List<DeviceActiveRoleEntity> findAllByRoleId(UUID roleId);
}
