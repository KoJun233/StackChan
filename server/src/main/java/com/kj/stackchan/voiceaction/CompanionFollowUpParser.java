package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

final class CompanionFollowUpParser {
    private static final String NUMBER = "[0-9零〇一二两三四五六七八九十]{1,3}";
    private static final Pattern REQUEST = Pattern.compile(
            "^(?:请你|请|你)?(.+?)(?:问问我|问我|关心一下)(.+?)[。！!？?]*$");
    private static final Pattern RELATIVE = Pattern.compile("^(" + NUMBER + ")(分钟|小时|天)后$");
    private static final Pattern LOCAL = Pattern.compile(
            "^(今天|明天|后天|\\d{4}-\\d{2}-\\d{2})(上午|早上|中午|下午|晚上)?(" + NUMBER
                    + ")[点时](?:(半)|(" + NUMBER + ")分?)?$");

    static boolean isRequest(String text) {
        return text.matches("^(?:请你|请|你)?(?:今天|明天|后天|[0-9一二两三四五六七八九十]).*(?:问问我|问我|关心一下).*$");
    }

    static VoiceActionDraft parse(String text, String zoneId, Clock clock) {
        var request = REQUEST.matcher(text.replaceAll("\\s+", ""));
        if (!request.matches()) return null;
        String topic = request.group(2).replaceFirst("[。！!？?]+$", "");
        if (topic.isBlank() || topic.length() > 120 || topic.matches("(?:一下|一声|结果|怎么样|近况)")) return null;
        ZoneId zone = ZoneId.of(zoneId);
        var now = clock.instant();
        java.time.Instant at;
        try {
            var relative = RELATIVE.matcher(request.group(1));
            if (relative.matches()) {
                int amount = number(relative.group(1));
                if (amount < 1 || amount > 43200) return null;
                Duration delay = switch (relative.group(2)) {
                    case "分钟" -> Duration.ofMinutes(amount);
                    case "小时" -> Duration.ofHours(amount);
                    default -> Duration.ofDays(amount);
                };
                at = now.plus(delay);
            } else {
                var local = LOCAL.matcher(request.group(1));
                if (!local.matches()) return null;
                LocalDate today = now.atZone(zone).toLocalDate();
                LocalDate date = switch (local.group(1)) {
                    case "今天" -> today;
                    case "明天" -> today.plusDays(1);
                    case "后天" -> today.plusDays(2);
                    default -> LocalDate.parse(local.group(1));
                };
                int hour = number(local.group(3));
                String period = local.group(2);
                if (period == null && hour > 0 && hour <= 12) return null;
                if (period != null) {
                    if (hour < 1 || hour > 12) return null;
                    if ("中午".equals(period)) {
                        if (hour == 1) hour = 13;
                        else if (hour != 11 && hour != 12) return null;
                    } else if ("晚上".equals(period)) {
                        if (hour < 6 || hour == 12) return null;
                        hour += 12;
                    } else if ("下午".equals(period)) {
                        if (hour < 12) hour += 12;
                    } else if (hour == 12) return null;
                }
                int minute = local.group(4) != null ? 30 : local.group(5) == null ? 0 : number(local.group(5));
                var localTime = date.atTime(LocalTime.of(hour, minute));
                var offsets = zone.getRules().getValidOffsets(localTime);
                if (offsets.size() != 1) return null;
                at = localTime.toInstant(offsets.getFirst());
            }
            if (!at.isAfter(now) || at.isAfter(now.plus(Duration.ofDays(30)))) return null;
        } catch (RuntimeException invalidTime) {
            return null;
        }
        String label = at.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "（" + zone.getId() + "）";
        return new VoiceActionDraft(VoiceActionType.CREATE_FOLLOW_UP, true, topic, label, at, zone.getId(),
                "NONE", 1, null, null, null, null, null);
    }

    private static int number(String text) {
        if (text.matches("[0-9]+")) return Integer.parseInt(text);
        if (text.equals("十")) return 10;
        int ten = text.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : digit(text.substring(0, ten));
            int units = ten == text.length() - 1 ? 0 : digit(text.substring(ten + 1));
            return tens * 10 + units;
        }
        return digit(text);
    }

    private static int digit(String text) {
        return switch (text) {
            case "零", "〇" -> 0;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> throw new IllegalArgumentException("Ambiguous number");
        };
    }
}
