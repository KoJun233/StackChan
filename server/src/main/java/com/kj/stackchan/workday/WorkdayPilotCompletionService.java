package com.kj.stackchan.workday;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.role.CompanionRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdayPilotCompletionService {

    private static final String TOPIC_PREFIX = "workday:pilot:complete:";
    private static final String PASS_CONTENT =
            "主人，十四天工作日陪伴观察已经完成，当前门槛已通过。请到管理页面查看详细报告，再决定下一阶段。";
    private static final String FAIL_CONTENT =
            "主人，十四天工作日陪伴观察已经完成，当前至少一项门槛未通过。请到管理页面查看详细报告后，再决定是否调整。";

    private final WorkdayPilotObservationRepository observationRepository;
    private final WorkdayPilotService pilotService;
    private final ReminderRepository reminderRepository;
    private final CompanionRoleService roleService;
    private final Clock clock;

    public WorkdayPilotCompletionService(
            WorkdayPilotObservationRepository observationRepository,
            WorkdayPilotService pilotService,
            ReminderRepository reminderRepository,
            CompanionRoleService roleService,
            Clock clock
    ) {
        this.observationRepository = observationRepository;
        this.pilotService = pilotService;
        this.reminderRepository = reminderRepository;
        this.roleService = roleService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> candidateDeviceIds() {
        return observationRepository.findAllByCompletionNotificationQueuedAtIsNullOrderByEndsOnAsc()
                .stream().map(WorkdayPilotObservationEntity::getDeviceId).toList();
    }

    @Transactional
    public boolean queueIfComplete(UUID deviceId) {
        WorkdayPilotObservationEntity observation = observationRepository.findForUpdate(deviceId)
                .orElse(null);
        if (observation == null || observation.getCompletionNotificationQueuedAt() != null) return false;

        WorkdayPilotService.PilotReportSnapshot report = pilotService.get(deviceId);
        if (!report.windowComplete()
                || report.status() == WorkdayPilotService.PilotStatus.COLLECTING
                || report.status() == WorkdayPilotService.PilotStatus.NOT_STARTED) {
            return false;
        }

        String topic = TOPIC_PREFIX + observation.getStartedOn() + ":" + report.status();
        var existing = reminderRepository
                .findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                        deviceId, ReminderSource.PROACTIVE, topic
                );
        var now = clock.instant();
        if (existing.isEmpty()) {
            CompanionRoleService.RoleSnapshot role = roleService.getActive(deviceId);
            String content = report.status() == WorkdayPilotService.PilotStatus.PASS
                    ? PASS_CONTENT : FAIL_CONTENT;
            reminderRepository.save(new ReminderEntity(
                    role.id(), deviceId, content, now, observation.getZoneId(),
                    ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                    topic, ProactiveGenerationStatus.FIXED, now
            ));
        }
        observation.markCompletionNotificationQueued(now);
        return existing.isEmpty();
    }
}
