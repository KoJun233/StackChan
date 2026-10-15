package com.kj.stackchan.delegation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.kj.stackchan.agent.AgentOrchestrator;
import com.kj.stackchan.agent.AgentToolPolicyInterceptor.ToolEvidence;
import static com.kj.stackchan.delegation.DelegationTaskExecutor.*;

/** Builds the one immutable run output from actual read evidence and bounded model suggestions. */
final class ProjectArtifactRenderer {
    static final String RULES = """
            这是一次只读项目成果，不是每日资讯。必须实际读取sourceUrls内每份材料，不得跟随页面新链接扩大范围。
            返回严格JSON，只有status和artifact。status为SUCCEEDED或BLOCKED。
            artifact只有understanding、options、recommendation、limitations、nextChoices。
            understanding与recommendation为中文字符串；limitations是字符串数组；nextChoices最多三个字符串。
            options数组按不同实际方案列出，每项只有name、identityQuote、evidenceIds、dimensions。
            identityQuote必须是实际来源中能辨识方案的原文，不能把同一方案的重复页面算成两个方案。
            每个dimension只有name、value、evidenceIds、quote。evidenceIds只用本次工具真实返回的E编号。
            已知事实的quote必须逐字出现在对应evidence.detail中，value仅概括该原文，不补造尺寸、价格或预算。
            没有来源事实时value严格写“来源未提供”，evidenceIds为空数组、quote为空字符串。
            比较维度由用户目标决定；占地、整理难度、线缆、可调整性等按相关目标逐项写，缺少的明确未知。
            不把两个链接当作两个方案；少于两个不同方案不能把比较标成功。
            recommendation只是依据已知事实和用户明确偏好的建议/推断，不替用户选定、购买、下单或认可成果。
            不声称用户已满意、已认可或实体布置完成。部分来源失败时保留读到的有限事实并标BLOCKED。
            previousOutput是原成果，preferenceFeedback是这次明确修订；同任务修订必须据此调整推荐依据，不能伪造旧版内容。
            不承诺新来源、登录、购买或外发；下一步由用户决定，最多三个可执行选择。
            """;

    TaskExecutionResult render(JsonNode root, AgentOrchestrator.TaskAgentResponse response, TaskExecutionRequest request) {
        var steps = response.steps().stream().map(step -> new ExecutionStep(step.toolName(),step.status(),step.summary())).toList();
        var context = request.projectContext();
        List<String> expected = PublicSourceScope.canonical(context.sourceUrls());
        var actual = response.evidence().stream().filter(item -> actualEvidence(item,response))
                .limit(20).toList();
        List<ExecutionEvidence> evidence = actual.stream().map(item -> new ExecutionEvidence(item.title(),item.url(),
                item.detail(),item.inputUrl(),item.readAt())).toList();
        List<String> problems = new ArrayList<>();
        for (String url : expected) if (response.sourceReads().stream().noneMatch(read -> read.succeeded()
                && url.equals(read.inputUrl()) && !read.evidenceIds().isEmpty()))
            problems.add("这份资料尚未成功读取：" + url + "。你可以补充可读页面后重新比较。");
        if (request.requiresToolEvidence() && (actual.isEmpty() || request.requiredToolNames().stream().anyMatch(name ->
                steps.stream().noneMatch(step -> name.equals(step.toolName()) && "SUCCEEDED".equals(step.status())))))
            problems.add("所需工具或可核对的来源证据不足，本次尚未完成。");
        try {
            only(root,Set.of("status","artifact"));
            String status = text(root,"status",20);
            if (!Set.of("SUCCEEDED","BLOCKED").contains(status)) throw invalid();
            JsonNode artifact = root.get("artifact");
            only(artifact,Set.of("understanding","options","recommendation","limitations","nextChoices"));
            String understanding = text(artifact,"understanding",2000);
            String recommendation = text(artifact,"recommendation",5000);
            List<String> limitations = strings(artifact.get("limitations"),20,1000);
            List<String> choices = strings(artifact.get("nextChoices"),3,1000);
            if (claimsUnperformedOutcome(understanding) || claimsUnperformedOutcome(recommendation)
                    || choices.stream().anyMatch(ProjectArtifactRenderer::claimsUnperformedOutcome)
                    || limitations.stream().anyMatch(ProjectArtifactRenderer::claimsUnperformedOutcome)) throw invalid();
            if (choices.isEmpty()) throw invalid();
            JsonNode options = artifact.get("options");
            if (options == null || !options.isArray() || options.isEmpty() || options.size()>12) throw invalid();
            StringBuilder comparison = new StringBuilder();
            Set<String> identities = new LinkedHashSet<>(), sourceSignatures = new LinkedHashSet<>();
            int distinctOptions = 0;
            for (JsonNode option : options) {
                only(option,Set.of("name","identityQuote","evidenceIds","dimensions"));
                String name = text(option,"name",200), identity = text(option,"identityQuote",300);
                List<ToolEvidence> optionEvidence = references(option.get("evidenceIds"),actual);
                boolean identityKnown = optionEvidence.stream().anyMatch(item -> contains(item.detail(),identity)
                        || contains(item.title(),identity));
                if (!expected.isEmpty() && !identityKnown) problems.add("“"+name+"”缺少可核对的方案身份，尚不能确认是独立方案。");
                String signature = optionEvidence.stream().map(item -> normalized(item.detail())).sorted()
                        .reduce("",(left,right)->left+"|"+right);
                if (identities.add(normalized(identity)) && (signature.isEmpty() || sourceSignatures.add(signature))) distinctOptions++;
                else problems.add("发现重复方案资料，不能作为两个不同方案比较。");
                comparison.append("### ").append(name).append('\n');
                JsonNode dimensions = option.get("dimensions");
                if (dimensions == null || !dimensions.isArray() || dimensions.isEmpty() || dimensions.size()>16) throw invalid();
                for (JsonNode dimension : dimensions) {
                    only(dimension,Set.of("name","value","evidenceIds","quote"));
                    String dimensionName = text(dimension,"name",100), value = text(dimension,"value",2000);
                    String quote = optionalText(dimension,"quote",2000);
                    List<ToolEvidence> refs = references(dimension.get("evidenceIds"),actual);
                    boolean unknown = "来源未提供".equals(value);
                    boolean generated = "TEXT_ONLY".equals(context.sourceMode()) && !request.requiresToolEvidence();
                    boolean supported = !refs.isEmpty() && !quote.isBlank() && refs.stream().allMatch(item -> contains(item.detail(),quote));
                    if (!unknown && !supported && !generated) {
                        problems.add("“"+name+" / "+dimensionName+"”没有可核对的来源原文，已保留为未知。");
                        value = "来源未提供";
                    }
                    comparison.append("- ").append(dimensionName).append("：").append(!unknown && supported ? quote : value);
                    if (generated) comparison.append("（生成内容）");
                    if (!unknown && supported) {
                        comparison.append("（来源 ").append(refs.stream().map(ToolEvidence::id).reduce((a,b)->a+"、"+b).orElse("")).append("；原文：")
                                .append(quote).append("）");
                    }
                    comparison.append('\n');
                }
                comparison.append('\n');
            }
            boolean comparisonRequested = java.util.regex.Pattern.compile("比较|对比|compar(?:e|ison)",java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(request.description()).find();
            if (comparisonRequested && distinctOptions < 2) problems.add("目前只有一个可确认的有效方案。请补充第二个不同方案后再比较。");
            if (context.supersedesRunId()!=null && (context.previousOutput()==null || context.previousOutput().isBlank()
                    || context.preferenceFeedback()==null || context.preferenceFeedback().isBlank()))
                problems.add("原成果或本次修改依据缺失，尚不能确认修订关系。");
            if (!problems.isEmpty()) {
                recommendation = "本次资料或证据仍不完整，暂不给出确定推荐；已核对的有限事实保留在上方。";
                limitations.addAll(problems);
                choices = List.of("补充缺少的资料或明确接受缩小后的范围，再请求重新比较。");
            }
            StringBuilder output = new StringBuilder("## 任务理解\n").append(understanding).append('\n');
            output.append("资料范围：").append(scope(context,expected)).append('\n');
            if (context.supersedesRunId()!=null) output.append("本次修订关联原成果 ").append(context.supersedesRunId())
                    .append("；你的修改：").append(context.preferenceFeedback()).append('\n');
            output.append("\n## 方案比较\n").append(comparison);
            output.append("## 建议与理由\n建议与推断（依据上列来源事实和你的明确偏好）：").append(recommendation).append('\n');
            output.append("\n## 缺失信息与限制\n");
            if (limitations.isEmpty()) output.append("- 本次只依据列明的公开资料；未提供的规格、成本和尺寸均未知。\n");
            else limitations.forEach(item -> output.append("- ").append(item).append('\n'));
            output.append("- 来源没有提供的尺寸、价格和预算保持未知；不能据此补猜或推荐。\n");
            if (!evidence.isEmpty()) {
                output.append("\n实际读取的来源：\n");
                actual.forEach(item -> {
                    output.append("- ").append(item.id()).append("：").append(item.title()).append("：").append(item.url());
                    if(!item.url().equals(item.inputUrl())) output.append("（实际读取入口 ").append(item.inputUrl()).append("）");
                    output.append("（读取时间 ").append(item.readAt()).append("）\n");
                });
            }
            output.append("\n## 下一步供你决定\n");
            choices.forEach(item -> output.append("- ").append(item).append('\n'));
            output.append("本结果等待你审阅；没有替你购买、下单或确认实体目标完成。");
            if (output.length()>32768) throw invalid();
            var urls=java.util.regex.Pattern.compile("https?://[^\\s<>\\\"\\)\\]，。；、（）]+",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(output);
            Set<String> knownUrls=new java.util.HashSet<>(expected);
            for(var item:evidence) knownUrls.add(PublicSourceScope.canonical(item.url()));
            while(urls.find()) if(!knownUrls.contains(PublicSourceScope.canonical(urls.group()))) throw invalid();
            return new TaskExecutionResult(output.toString(),"SUCCEEDED".equals(status) && problems.isEmpty()
                    ? DelegationService.RunStatus.SUCCEEDED : DelegationService.RunStatus.BLOCKED,steps,evidence);
        } catch (RuntimeException invalid) {
            return fallback(request,steps,evidence,problems);
        }
    }

    private static boolean actualEvidence(ToolEvidence item,AgentOrchestrator.TaskAgentResponse response) {
        if (item.inputUrl()==null || item.readAt()==null) return false;
        try { PublicSourceScope.canonical(item.url()); }
        catch (RuntimeException invalid) { return false; }
        return response.sourceReads().stream().anyMatch(read -> read.succeeded() && item.inputUrl().equals(read.inputUrl())
                && read.evidenceIds().contains(item.id()));
    }
    private static TaskExecutionResult fallback(TaskExecutionRequest request,List<ExecutionStep> steps,
                                                List<ExecutionEvidence> evidence,List<String> problems) {
        StringBuilder output = new StringBuilder("## 任务理解\n按你保存的目标处理，只使用已授权资料。\n\n## 方案比较\n");
        if (evidence.isEmpty()) output.append("尚未获得可核对的来源事实。\n");
        else evidence.stream().limit(4).forEach(item -> output.append("- ").append(shorten(item.title(),200)).append("：来源原文摘录：")
                .append(shorten(item.detail(),600)).append("（").append(item.url()).append("；读取时间 ").append(item.readAt()).append("）\n"));
        output.append("\n## 建议与理由\n成果结构或证据尚不完整，暂不能给出可靠比较和推荐。\n\n## 缺失信息与限制\n")
                .append("来源未提供或尚未核实的信息保持未知。\n");
        problems.stream().limit(8).forEach(problem -> output.append("- ").append(shorten(problem,2300)).append('\n'));
        output.append("\n## 下一步供你决定\n- 补充可读来源，或明确要求在现有范围重新比较。\n本次尚未完成，没有执行购买或外发。");
        return new TaskExecutionResult(output.substring(0,Math.min(output.length(),32768)),DelegationService.RunStatus.BLOCKED,steps,evidence);
    }
    private static String scope(ProjectExecutionContext context,List<String> expected) {
        return expected.isEmpty() ? "TEXT_ONLY".equals(context.sourceMode()) ? "仅本地文字生成" : "已明确允许的公开只读资料"
                : String.join("、",expected);
    }
    private static List<ToolEvidence> references(JsonNode ids,List<ToolEvidence> evidence) {
        List<String> requested = strings(ids,20,20);
        List<ToolEvidence> result = new ArrayList<>();
        for (String id : requested) result.add(evidence.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(ProjectArtifactRenderer::invalid));
        return result;
    }
    private static void only(JsonNode value,Set<String> fields) {
        if (value==null || !value.isObject() || value.size()!=fields.size()) throw invalid();
        value.fieldNames().forEachRemaining(name -> { if (!fields.contains(name)) throw invalid(); });
    }
    private static String text(JsonNode value,String field,int max) {
        String result = optionalText(value,field,max);
        if (result.isBlank()) throw invalid();
        return result;
    }
    private static String optionalText(JsonNode value,String field,int max) {
        JsonNode result = value.get(field);
        if (result==null || !result.isTextual() || result.textValue().length()>max) throw invalid();
        return result.textValue().strip();
    }
    private static List<String> strings(JsonNode value,int max,int itemMax) {
        if (value==null || !value.isArray() || value.size()>max) throw invalid();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length()>itemMax) throw invalid();
            result.add(item.textValue().strip());
        }
        return result;
    }
    private static boolean contains(String source,String quote) {
        return source!=null && !quote.isBlank() && source.contains(quote);
    }
    private static String normalized(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]",""); }
    private static boolean claimsUnperformedOutcome(String value) {
        return java.util.regex.Pattern.compile("已(?:经)?(?:为你|替你|帮你)(?:选定|购买|下单|支付|完成布置)"
                +"|已(?:经)?(?:下单|购买|支付|完成布置)|(?:用户|你)(?:已经|已)(?:满意|认可|确认完成|读过|看过)"
                +"|已(?:经)?(?:获你|获得你)(?:认可|接受)|用户(?:很|非常)满意|(?:手机|通知|推送)(?:已经|已)(?:送达|读|阅读)")
                .matcher(value).find();
    }
    private static String shorten(String value,int length) { return value==null?"":value.substring(0,Math.min(value.length(),length)); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("PROJECT_ARTIFACT_INVALID"); }
}
