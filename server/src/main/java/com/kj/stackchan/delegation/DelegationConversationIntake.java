package com.kj.stackchan.delegation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import com.kj.stackchan.agent.AgentChannel;
import com.kj.stackchan.agent.AgentInvocationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.kj.stackchan.delegation.DelegationService.*;

/** Only explicit user commands enter here; neither the model nor a URL can choose the resource scope. */
@Service
public class DelegationConversationIntake {
    private static final Duration DRAFT_TTL=Duration.ofMinutes(10);
    private static final String EXPIRED="上一项补充已过期，请重新交代目标。";
    private static final Pattern SCHEDULE=Pattern.compile("每天|每日|每周|每隔|定时|明天|后天|今天.{0,16}[0-9一二三四五六七八九十两]+(?:点|[:：])");
    private static final Pattern INTENT=Pattern.compile("^(?:请|帮我|替我|为我|我想|我希望|以后|每天|每日|每周|每隔|定时|明天|后天|现在)|(?:帮我|替我|给我|请你)");
    private static final Pattern ONCE_WORK=Pattern.compile("(?:(?:帮我|替我|为我|请你)\\s*(?:现在|先|马上|立即)?|^(?:请|现在|先)(?:你)?)\\s*(?:比较|对比|整理|研究|分析|写一份|写一篇|写个|制作一份|做一份|汇总)");
    private static final Pattern EXAMPLE=Pattern.compile("^(?:例如|比如|假如|举个例子|如果|假设|举例|示例|他说|她说|他们说|原话|引用|[“‘\"'])|(?:只是|仅作|作为).{0,8}(?:例子|示例)");
    private static final Pattern ABILITY=Pattern.compile("(?:能不能|是否能|能否|可不可以|会不会).{0,30}(?:比较|对比|整理|研究|分析|写|制作|任务|委托)|^(?:你|伙伴|机器人)?(?:能|可以|支持).{0,30}(?:吗|么|[？?])");
    private static final Pattern RESEARCH_WORK=Pattern.compile("比较|对比|整理|研究|分析|写(?:一份|一篇|一个|个|作)|制作(?:一份|一个)|汇总");
    private static final Pattern DECLINE=Pattern.compile("^(?:请|我)?(?:取消|算了|不用了|不要了|不需要|不想|不希望|先不|停止|别再|不要|别)");
    private static final Pattern BARE_CANCEL=Pattern.compile("^(?:取消|停止|算了|不用了|不要了)[\\s。！!]*$");
    private static final Pattern TASK_QUESTION=Pattern.compile("(?:任务|委托).{0,8}(?:有哪些|状态|结果|怎么样|进展)");
    private static final Pattern QUERY=Pattern.compile("(?:比较|研究|整理|报告|委托).{0,18}(?:到哪|怎么样|结果|进展|成果|完成|好了|状态)|(?:昨天|前天|之前|上次).{0,12}(?:那个|那份|那件).{0,8}(?:到哪|怎么样|结果|进展)|(?:看看|查看|读一下).{0,12}(?:比较|报告|成果)");
    private static final Pattern ACCEPT=Pattern.compile("^(?:谢谢[，,]?|我觉得|我认为)?(?:这份|这次|这个|当前|最新)?(?:比较|结果|成果|报告).{0,8}(?:可以了|认可|满意|采用|接受)|^(?:请|我(?:明确)?)?(?:认可|接受|采用).{0,6}(?:这份|这个|结果|成果|比较)");
    private static final Pattern REVISE=Pattern.compile("修改偏好|调整偏好|重新(?:比较|对比|整理|研究|分析)|重做(?:这份|这个|比较|结果|报告)");
    private static final Pattern RUN_REQUEST=Pattern.compile("重新(?:比较|对比|整理|研究|分析)|重做(?:这份|这个|比较|结果|报告)|保存并(?:执行|比较)");
    private static final Pattern FEEDBACK_INTENT=Pattern.compile("^(?:请|帮我|我(?:想|希望|更|现在)|这项|这个|这份|按|重新|修改|调整|重做|再)");
    private static final Pattern MUTATION_QUESTION=Pattern.compile("(?:吗|么|[？?])[\\s。！!]*$|是什么意思|的意思|怎么|如何|为什么|会怎样|会怎么样|能否|是否|可否|可不可以|能不能");
    private static final Pattern NEGATIVE_ACCEPT=Pattern.compile("(?:不|没|未|别|不要|不用|不能|不想|不予|尚未|还没|还不).{0,8}(?:满意|认可|接受|采用|可以)");
    private static final Pattern NEGATIVE_REVISION=Pattern.compile("(?:不要|不用|别|无需|不想|不需要)(?:再|帮我|替我|为我|让你|你|现在|先|进行|这个|这次|去|来|给我){0,4}(?:重新|重做|修改偏好|调整偏好)");
    private static final Pattern PAUSE=Pattern.compile("^(?:请|帮我|先)?暂停.{0,18}(?:任务|委托|比较|研究|整理|报告)|^(?:把|先把).{1,30}(?:任务|比较|委托).{0,8}暂停");
    private static final Pattern CANCEL=Pattern.compile("^(?:请|帮我)?取消.{0,18}(?:任务|委托|比较|研究|整理|报告)|^(?:把|先把).{1,30}(?:任务|比较|委托).{0,8}取消");
    private static final Pattern CONTINUE=Pattern.compile("^(?:请|帮我)?(?:继续|重新执行).{0,18}(?:这个比较|这项比较|这个任务|这项任务|这个委托|这项委托|之前的比较)[\\s。！!]*$");
    private static final Pattern SAVE_ONLY=Pattern.compile("只保存|先保存|稍后(?:再)?(?:执行|比较)|先不(?:重新)?(?:执行|比较)");
    private final DelegationService tasks;
    private final Clock clock;
    private final ConcurrentHashMap<Key,Draft> pending=new ConcurrentHashMap<>();

    public DelegationConversationIntake(@Lazy DelegationService tasks,JdbcTemplate jdbc,Clock clock) {
        this.tasks=tasks; this.clock=clock;
    }

    public boolean supports(AgentInvocationContext context,String message) {
        if(context==null || context.channel()==AgentChannel.DELEGATION || message==null) return false;
        // Explain an expired draft instead of inheriting its authorization.
        pending.entrySet().removeIf(entry->entry.getValue().expiresAt().plus(Duration.ofHours(24)).isBefore(clock.instant()));
        if(pending.containsKey(key(context))) return true;
        String text=message.strip();
        if(nonActionResearch(text)) return true;
        if(ignored(text)) return false;
        return query(text) || feedbackKind(text)!=null || CONTINUE.matcher(text).matches() || createIntent(text);
    }

    public boolean requiresPlanning(AgentInvocationContext context,String message) {
        String text=message==null?"":message.strip();
        var draft=pending.get(key(context));
        return !ignored(text) && !BARE_CANCEL.matcher(text).matches() && !query(text)
                && feedbackKind(text)==null && !CONTINUE.matcher(text).matches()
                && (draft==null || createIntent(text) || draft.kind()==DraftKind.CREATE && !expired(draft));
    }

    public String accept(AgentInvocationContext context,String message) {
        Key scope=key(context); String text=message.strip();
        Draft draft=pending.get(scope);
        if(draft!=null && BARE_CANCEL.matcher(text).matches()) {
            pending.remove(scope); return "好的，这项尚未保存的补充取消了。";
        }
        if(draft!=null && expired(draft)) {
            pending.remove(scope);
            if(!createIntent(text)) return EXPIRED;
            draft=null;
        }
        if(draft!=null && draft.kind()!=DraftKind.CREATE && createIntent(text) && ONCE_WORK.matcher(text).find()) {
            pending.remove(scope); draft=null;
        }
        if(ignored(text)) return "比较和整理需要当前授权及实际来源。请交代要执行的目标和公开 HTTPS 页面；现在没有保存任务，也没有读取页面。";
        if(context.deviceId()==null || context.roleId()==null || context.turnId()==null)
            return "任务还未保存。请在控制台使用已绑定设备的对话，再把这项目标交给当前伙伴。";
        if(draft!=null && draft.kind()==DraftKind.SELECT) {
            var number=Pattern.compile("^(?:第)?([1-5])(?:项|个)?[。]?$").matcher(text);
            var matches=number.matches() && Integer.parseInt(number.group(1))<=draft.choices().size()
                    ?List.of(draft.choices().get(Integer.parseInt(number.group(1))-1))
                    :draft.choices().stream().filter(task->text.equals(task.title()) || text.equals(task.id().toString())).toList();
            if(matches.size()!=1) return selectionQuestion(draft.choices());
            pending.remove(scope);
            return act(context,matches.getFirst(),draft.description());
        }
        if(draft!=null && draft.kind()==DraftKind.PREFERENCE) {
            pending.remove(scope);
            return submitFeedback(context,draft.choices().getFirst(),FeedbackKind.REVISE_PREFERENCES,text);
        }
        if(query(text) || feedbackKind(text)!=null || CONTINUE.matcher(text).matches()) {
            pending.remove(scope);
            var candidates=tasks.findProjectTasks(context.deviceId(),context.roleId());
            var choices=resolve(candidates,text);
            if(choices.isEmpty()) return "当前伙伴还没有可对应的项目。请先交代一个明确目标，已有委托也可以在持续委托中查看。";
            if(choices.size()!=1) {
                remember(scope,new Draft(DraftKind.SELECT,text,choices,clock.instant().plus(DRAFT_TTL)));
                return selectionQuestion(choices);
            }
            return act(context,choices.getFirst(),text);
        }
        if(DECLINE.matcher(text).find()) { pending.remove(scope); return "好的，这个尚未保存的任务取消了。"; }
        if(draft==null && !createIntent(text)) return "任务还未保存。请先说明希望实际交付的目标。";
        String description=draft!=null && draft.kind()==DraftKind.CREATE && !ONCE_WORK.matcher(text).find()
                ?draft.description()+"\n补充说明："+text:text;
        try {
            var task=tasks.createProject(new TaskInput(context.deviceId(),context.roleId(),null,null,List.of(),
                    null,List.of(),null,null,0,null,true,description,null,null),context.turnId().toString());
            pending.remove(scope);
            return acknowledgement(task);
        } catch(DelegationException error) {
            if(error.clarification()!=null) {
                if(description.length()<3500) remember(scope,new Draft(DraftKind.CREATE,description,List.of(),clock.instant().plus(DRAFT_TTL)));
                return "任务还未保存。"+error.clarification();
            }
            return errorReply(error);
        }
    }

    private String act(AgentInvocationContext context,TaskSnapshot task,String message) {
        if(query(message)) return tasks.projectConversationSummary(task.id());
        if(CONTINUE.matcher(message).matches()) {
            try {
                tasks.runRevision(task.id(),task.revision(),context.turnId().toString());
                return "已记录继续这项比较的请求。请在持续委托查看执行状态，已有成果和来源仍保留。";
            } catch(DelegationException error) { return errorReply(error); }
        }
        FeedbackKind kind=feedbackKind(message);
        if(kind==FeedbackKind.REVISE_PREFERENCES && message.matches("(?:请|帮我)?(?:修改|调整)偏好[。！!]*")) {
            remember(key(context),new Draft(DraftKind.PREFERENCE,message,List.of(task),clock.instant().plus(DRAFT_TTL)));
            return "你想把哪一条偏好改成什么？这次修改还未保存，只影响这项任务。";
        }
        return submitFeedback(context,task,kind,message);
    }

    private String submitFeedback(AgentInvocationContext context,TaskSnapshot task,FeedbackKind kind,String comment) {
        if(kind==null) return "请说明要查看、认可、修改偏好、暂停还是取消哪项任务。";
        var run=task.lastRun();
        if(run==null || run.review()==null) return "这项任务还没有可审阅的成果，请先在持续委托查看执行状态。";
        RunMode mode=kind==FeedbackKind.REVISE_PREFERENCES
                ?SAVE_ONLY.matcher(comment).find() || !RUN_REQUEST.matcher(comment).find()?RunMode.SAVE_ONLY:RunMode.SAVE_AND_RUN:null;
        try {
            tasks.feedback(run.id(),new FeedbackInput(kind,comment,task.revision(),run.review().resourceVersion(),mode),context.turnId().toString());
            return switch(kind) {
                case ACCEPT_RESULT -> "已记录你认可这份比较成果，成果与来源会保留在持续委托。";
                case REVISE_PREFERENCES -> mode==RunMode.SAVE_ONLY
                        ?"偏好已保存，暂未重新比较；你准备好后可以说“继续这个比较”。原成果与来源仍保留。"
                        :"偏好已保存，重新比较的请求已记录。原成果与来源仍保留，可在持续委托查看新结果。";
                case PAUSE -> "这项任务已暂停，后续执行已停下，已有成果仍可查看。";
                case CANCEL -> "这项任务已取消，不再执行；取消原因、已有成果与来源仍可查看。";
            };
        } catch(DelegationException error) { return errorReply(error); }
    }

    private static List<TaskSnapshot> resolve(List<TaskSnapshot> candidates,String message) {
        var explicit=candidates.stream().filter(task->message.contains(task.id().toString()) || message.contains(task.title())).toList();
        if(!explicit.isEmpty()) return explicit;
        String subject=message.replaceAll("昨天|前天|之前|上次|那个|那份|那件|这个|这份|当前|最新|比较|对比|方案|整理|研究|报告|任务|委托|到哪了|到哪|怎么样|结果|进展|成果|完成|好了|状态|[\\s，。？！、；:：,.!?]","")
                .replaceAll("(?:了|吗|呢|呀|啊|么)+$","");
        if(query(message) && subject.codePointCount(0,subject.length())>=4) {
            return candidates.stream().filter(task->task.title().contains(subject)
                    || task.description()!=null && task.description().contains(subject)).toList();
        }
        return candidates;
    }
    private static String selectionQuestion(List<TaskSnapshot> choices) {
        String names=java.util.stream.IntStream.range(0,Math.min(5,choices.size())).mapToObj(i->(i+1)+"："+choices.get(i).title())
                .collect(java.util.stream.Collectors.joining("；"));
        return "有多项可能对应的任务，请回复列表序号、完整标题或任务编号："+names+"。也可以在持续委托中选择；还没有执行这次操作。";
    }
    private static boolean ignored(String text) { return EXAMPLE.matcher(text).find() || ABILITY.matcher(text).find(); }
    private static boolean nonActionResearch(String text) {
        return RESEARCH_WORK.matcher(text).find() && (ABILITY.matcher(text).find()
                || EXAMPLE.matcher(text).find() && INTENT.matcher(text).find());
    }
    private static boolean createIntent(String text) {
        return !ignored(text) && !DECLINE.matcher(text).find() && !TASK_QUESTION.matcher(text).find() && INTENT.matcher(text).find()
                && (SCHEDULE.matcher(text).find() || ONCE_WORK.matcher(text).find());
    }
    private static boolean query(String text) { return QUERY.matcher(text).find() && feedbackKind(text)==null && !createIntent(text); }
    private static FeedbackKind feedbackKind(String text) {
        if(MUTATION_QUESTION.matcher(text).find()) return null;
        if(CANCEL.matcher(text).find()) return FeedbackKind.CANCEL;
        if(PAUSE.matcher(text).find()) return FeedbackKind.PAUSE;
        if(!DECLINE.matcher(text).find() && !NEGATIVE_REVISION.matcher(text).find()
                && FEEDBACK_INTENT.matcher(text).find() && REVISE.matcher(text).find()) return FeedbackKind.REVISE_PREFERENCES;
        return !NEGATIVE_ACCEPT.matcher(text).find() && ACCEPT.matcher(text).find()?FeedbackKind.ACCEPT_RESULT:null;
    }
    private static String acknowledgement(TaskSnapshot task) {
        var run=task.lastRun();
        if(run!=null && run.status()==RunStatus.FAILED) {
            if("DELEGATION_CAPACITY".equals(run.errorCode())) return "任务已保存，当前有任务在执行，这次尚未完成。请稍后在持续委托查看状态，再明确重试一次。";
            return "任务已保存，这次执行尚未完成。请在持续委托查看原因，再决定是否重试一次。";
        }
        String schedule=switch(task.scheduleType()) {
            case ONCE -> task.nextRunAt()==null?"先做一次":java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
                    .format(task.onceAt().atZone(java.time.ZoneId.of(task.zoneId())))+"做一次";
            case DAILY -> "每天"+task.localTime();
            case WEEKLY -> "每周按保存的日期与时间";
            case INTERVAL -> "每隔"+task.intervalMinutes()+"分钟";
        };
        String source=task.sourceUrls().isEmpty()?"按允许的资料范围处理":"只读取你给定的"+task.sourceUrls().size()+"份公开资料";
        String delivery=task.deliveryMode()==DeliveryMode.BARK?"按授权交付手机，网页保留成果和来源":"成果和来源放在持续委托";
        return "已接下「"+task.title()+"」，"+schedule+"。我会"+source+"，"+delivery+"；未获授权的操作不会执行。";
    }
    private String errorReply(DelegationException error) {
        if("DELEGATION_PUSH_NOT_CONFIGURED".equals(error.code())) return "任务还没有保存。请先在持续委托中配置并启用 Bark，再交给我。";
        if("REVISION_CONFLICT".equals(error.code())) return "这项任务或成果刚有更新，这次操作未保存。请重新查看成果后再决定。";
        if("IDEMPOTENCY_CONFLICT".equals(error.code())) return "这次请求与原操作不一致，请先在持续委托核对已保存的状态。";
        if("EXECUTION_CAPACITY_BUSY".equals(error.code()) || "DELEGATION_CAPACITY".equals(error.code())) return "当前有任务在执行，请稍后在持续委托核对状态，再决定是否重试。";
        return "这次操作未完成。请在持续委托查看已保存的任务与成果，再决定下一步。";
    }
    private void remember(Key key,Draft draft) { if(pending.size()<256 || pending.containsKey(key)) pending.put(key,draft); }
    private boolean expired(Draft draft) { return !draft.expiresAt().isAfter(clock.instant()); }
    private static Key key(AgentInvocationContext context) {
        return new Key(context.conversationId()==null?context.turnId():context.conversationId(),context.deviceId(),context.roleId());
    }
    private record Key(UUID conversation,UUID device,UUID role) {}
    private enum DraftKind { CREATE,SELECT,PREFERENCE }
    private record Draft(DraftKind kind,String description,List<TaskSnapshot> choices,Instant expiresAt) {}
}
