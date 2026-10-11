package com.kj.stackchan.delegation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import com.kj.stackchan.agent.AgentInvocationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Binds task creation to the user's current conversation; model output cannot choose device or role. */
@Service
public class DelegationConversationIntake {
    private static final Pattern SCHEDULE = Pattern.compile("每天|每日|每周|每隔|定时|明天|后天|今天.{0,16}[0-9一二三四五六七八九十两]+(?:点|[:：])");
    private static final Pattern INTENT = Pattern.compile("^(?:请|帮我|替我|为我|我想|我希望|以后|每天|每日|每周|每隔|定时|明天|后天)|(?:帮我|替我|给我|请你)");
    private static final Pattern QUESTION_OR_EXAMPLE = Pattern.compile("^(?:例如|比如|假如|举个例子|如果)|只是.{0,8}例子|(?:任务|委托).{0,8}(?:有哪些|状态|结果|怎么样|进展)");
    private static final Pattern DECLINE = Pattern.compile("^(?:取消|算了|不用了|不要了|先不|停止|别再)");
    private final DelegationService tasks;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConcurrentHashMap<Key,Pending> pending=new ConcurrentHashMap<>();
    public DelegationConversationIntake(@Lazy DelegationService tasks,JdbcTemplate jdbc,Clock clock) {
        this.tasks=tasks; this.jdbc=jdbc; this.clock=clock;
    }
    public boolean supports(AgentInvocationContext context,String message) {
        if(context==null || context.channel()==com.kj.stackchan.agent.AgentChannel.DELEGATION) return false;
        pending.entrySet().removeIf(entry->entry.getValue().expiresAt().isBefore(clock.instant()));
        if(pending.containsKey(key(context))) return true;
        String text=message.strip();
        return !DECLINE.matcher(text).find() && !QUESTION_OR_EXAMPLE.matcher(text).find()
                && SCHEDULE.matcher(text).find() && INTENT.matcher(text).find();
    }
    public String accept(AgentInvocationContext context,String message) {
        var key=key(context);
        if(DECLINE.matcher(message.strip()).find()) { pending.remove(key); return "好的，这个尚未保存的任务取消了。"; }
        UUID device=context.deviceId();
        if(device==null) {
            List<UUID> devices=jdbc.query("select id from devices order by id limit 2",(rs,row)->rs.getObject(1,UUID.class));
            if(devices.size()!=1) return "请先在控制台选择一个设备，再把这项任务交给我。";
            device=devices.getFirst();
        }
        String description=message;
        var prior=pending.get(key);
        if(prior!=null) description=prior.description()+"\n补充说明："+message;
        try {
            var task=tasks.create(new DelegationService.TaskInput(device,context.roleId(),null,null,List.of(),
                    null,List.of(),null,null,0,null,true,description,null,null));
            pending.remove(key);
            return "已接下："+task.understanding()+" 你可以在持续委托中查看安排和执行记录。";
        } catch(DelegationException error) {
            if(error.clarification()!=null) {
                if(pending.size()<256 && description.length()<3500)
                    pending.put(key,new Pending(description,clock.instant().plus(Duration.ofMinutes(10))));
                return error.clarification();
            }
            if("DELEGATION_PUSH_NOT_CONFIGURED".equals(error.code())) return "任务还没有保存。请先在持续委托中配置并启用 Bark，再交给我。";
            return "这项任务还没有保存，请在持续委托中检查安排与交付设置后重试。";
        }
    }
    private static Key key(AgentInvocationContext context) {
        return new Key(context.conversationId()==null?context.turnId():context.conversationId(),context.deviceId(),context.roleId());
    }
    private record Key(UUID conversation,UUID device,UUID role) {}
    private record Pending(String description,Instant expiresAt) {}
}
