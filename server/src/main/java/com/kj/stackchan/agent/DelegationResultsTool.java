package com.kj.stackchan.agent;

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.kj.stackchan.delegation.DelegationService;
import com.kj.stackchan.delegation.DelegationTaskExecutor;
import org.springframework.ai.tool.annotation.Tool;

/** Reads saved work only; device and role are supplied by the server. */
public class DelegationResultsTool {
    public static final String ID = "current_delegation_results";
    private final UUID deviceId;
    private final UUID roleId;
    private final DelegationService service;
    private final ObjectMapper mapper;

    public DelegationResultsTool(UUID deviceId, UUID roleId, DelegationService service, ObjectMapper mapper) {
        this.deviceId = java.util.Objects.requireNonNull(deviceId);
        this.roleId = java.util.Objects.requireNonNull(roleId);
        this.service = service;
        this.mapper = mapper;
    }

    @Tool(name = ID, description = "读取当前设备与伙伴最近已保存的通用任务结果、实际执行步骤和来源。output是保存的结果，BLOCKED表示缺能力或证据，hasMoreItems表示还有内容；日期来自运行记录，空列表不表示任务完成。不得创建任务、重新执行或推送，SENT只代表渠道接受。")
    public String currentResults() {
        ObjectNode saved=mapper.createObjectNode(); ArrayNode results=saved.putArray("results");
        for(var run:service.latestForDeviceRole(deviceId,roleId).stream().limit(3).toList()) {
            var result=results.addObject();
            result.put("title",bounded(run.taskTitle(),100)); result.put("localDate",run.scheduledDate().toString());
            result.put("scheduledAt",run.scheduledAt()==null?null:run.scheduledAt().toString());
            result.put("status",run.status().name()); result.put("deliveryStatus",run.deliveryStatus().name());
            result.put("completedAt",run.completedAt()==null?null:run.completedAt().toString());
            result.put("output",bounded(run.output(),1000));
            result.set("steps",mapper.valueToTree(run.steps().stream().limit(5).map(step->
                    new DelegationTaskExecutor.ExecutionStep(step.toolName(),step.status(),bounded(step.summary(),100))).toList()));
            result.set("evidence",mapper.valueToTree(run.evidence().stream().limit(3).map(evidence->
                    new DelegationTaskExecutor.ExecutionEvidence(bounded(evidence.title(),100),evidence.url(),bounded(evidence.detail(),100))).toList()));
            result.set("items",mapper.valueToTree(run.items().stream().limit(3).map(item->new Item(
                    bounded(item.title(),100),bounded(item.summary(),100),bounded(item.impact(),80),
                    bounded(item.sourceName(),60),item.url(),item.publishedAt().toString())).toList()));
            result.put("hasMoreItems",run.items().size()>3 || run.evidence().size()>3 || run.steps().size()>5
                    || run.output()!=null && run.output().codePointCount(0,run.output().length())>1000);
        }
        try {
            // Preserve complete citations and valid JSON within the agent's default tool-result budget.
            while (true) {
                String json = mapper.writeValueAsString(saved);
                if (json.getBytes(StandardCharsets.UTF_8).length <= 8192 || results.isEmpty()) return json;
                int index = results.size() - 1;
                ObjectNode last=(ObjectNode)results.get(index);
                ArrayNode items=(ArrayNode)last.get("items"), evidence=(ArrayNode)last.get("evidence");
                if(evidence.size()>1) evidence.remove(evidence.size()-1);
                else if(items.size()>1) items.remove(items.size()-1);
                else if(last.path("output").asText().length()>100) last.put("output",bounded(last.path("output").asText(),100));
                else results.remove(index);
                last.put("hasMoreItems",true);
            }
        } catch (JsonProcessingException ignored) {
            throw new IllegalStateException("Could not serialize delegation results");
        }
    }

    private static String bounded(String text, int length) {
        if (text == null) return "";
        return text.codePoints().limit(length).collect(StringBuilder::new, StringBuilder::appendCodePoint,
                StringBuilder::append).toString();
    }
    private record Item(String title, String summary, String impact, String sourceName, String url, String publishedAt) {}
}
